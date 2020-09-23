package cn.bridgeli.middleware.sequence.core;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 序号对象
 * <p/>
 * 序号范围会在本地内存中存储和命中率分析，所以设计本类的hashCode和equals均只涉及seqName，排序使用lastUpdate
 *
 * @author bridgeli
 */
@Data
public class Sequence implements Comparable<Sequence> {

    private static final Logger LOGGER = LoggerFactory.getLogger("GLOBAL-SEQUENCE");

    public static final String LOOP_YES = "Y";
    public static final String LOOP_NO = "N";

    /**
     * 距离Long.MAX 不足10亿时日志提醒
     */
    private static final long SHORTAGE_WARN = 10000 * 10000 * 10;

    private final AtomicLongFieldUpdater<Sequence> currentFieldUpdater = AtomicLongFieldUpdater.newUpdater(Sequence.class, "current");

    /**
     * 序号类别
     */
    private String name;
    /**
     * 当前可用序号
     */
    private volatile long current;
    /**
     * 最大序号(不包含)
     */
    private long max;
    /**
     * 最小序号(包含)
     */
    private long min;
    /**
     * 序号使用完是否循环使用 Y-循环使用 N-达到最大值报错
     */
    private String loop;
    /**
     * 步长
     */
    private long step;
    /**
     * 一次查询支持的序号数量
     */
    private long count;
    /**
     * 内存中最后更新时间
     */
    private long lastUpdate = System.currentTimeMillis();

    @Override
    public int compareTo(Sequence arg0) {
        if (arg0 == null) {
            return -1;
        }
        if (arg0.getLastUpdate() > this.getLastUpdate()) {
            return -1;
        } else if (arg0.getLastUpdate() == this.getLastUpdate()) {
            return 0;
        } else {
            return 1;
        }
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public boolean equals(Object arg0) {
        if (arg0 == null || arg0.getClass() != this.getClass()) {
            return false;
        }
        Sequence sequence = (Sequence) arg0;
        return this.name.equals(sequence.getName());
    }

    /**
     * 使用CAS操作来自增一个step
     *
     * @return
     */
    public long getCurrentAndIncrementStep() {
        for (; ; ) {
            long expect = this.getCurrent();
            long update;
            if (this.getMax() - this.getCurrent() > this.getStep()) {
                update = this.getCurrent() + this.getStep();
            } else {
                update = this.getMax();
            }
            if (expect == update) {
                throw new CASEqualsException();
            }
            if (currentFieldUpdater.compareAndSet(this, expect, update)) {
                return expect;
            }
        }

    }

    /**
     * 从数据库更新本地序号范围，考虑数据库的序号范围已经为最大值和本地序号范围不能溢出
     * <p/>
     * 序号范围的所有配置项均从数据库更新，保证修改数据库后本地内存及时生效
     */
    public void updateFromDB(Sequence seqFromDB) {
        if (seqFromDB == null) {
            throw new SequenceException("序号数据库记录异常!可能数据库记录被删除,后续可能产生重复id!!");
        }

        this.setCurrent(seqFromDB.getCurrent());
        this.setLoop(seqFromDB.getLoop());
        this.setStep(seqFromDB.getStep());
        this.setCount(seqFromDB.getCount());

        if (this.getCurrent() >= seqFromDB.getMax()) {
            if (LOOP_NO.equals(this.getLoop())) {
                throw new SequenceException("序号已使用完，请调整!!!");
            } else {
                // 从头开始使用
                this.setCurrent(seqFromDB.getMin());
            }
        } else if (seqFromDB.getMax() - this.getCurrent() < SHORTAGE_WARN && LOOP_NO.equals(this.getLoop())) {
            LOGGER.warn(String.format("%s全局序列号已经不足，剩余%d,请尽快处理，改进方案！！！", this.getName(), seqFromDB.getMax() - this.getCurrent()));
        }

        // 保证countStep没有溢出而且小于最大值和当前值差额，否则直接把当前到最大值这一段取走
        long countStep = this.getCount() * this.getStep();
        if (seqFromDB.getMax() - this.getCurrent() > countStep && countStep >= this.getCount() && countStep >= this.getStep()) {
            this.setMax(this.getCurrent() + this.getCount() * this.getStep());
        } else {
            this.setMax(seqFromDB.getMax());
        }
        this.setMin(this.getCurrent());
    }
}
