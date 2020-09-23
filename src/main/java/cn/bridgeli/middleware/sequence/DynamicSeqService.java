package cn.bridgeli.middleware.sequence;

import cn.bridgeli.middleware.sequence.core.SequenceContext;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 全局序号生成服务
 * <p>
 * 原理： 数据库中记录一条数据：包含最小值、最大值、当前值等 jvm中使用该全局序号时，从这条记录中的当前值开始取一个数据段，并将这条记录的当前值向后移动这个数据段的长度
 * 这个jvm拿到这个数字范围后，可以在这个范围内增长获取数字num，当这个num达到数字范围的最大值后，这个jvm再次到数据库中的这条记录上取一段数字
 * <p>
 * 不同jvm取到不重合的数字范围，同一个jvm在一个数字范围内玩耍，则可保证多个jvm使用同一个序列时num不发生冲突
 * <p>
 * 效率问题不用担心，每次取一个数字范围，这个数字范围使用完之后才会和数据库交互
 * <p>
 * 分两种模式， <br/>
 * 一种是dynamic，即seqName不存在时自动在数据库中创建，sequence对象的属性由getNextSeq方法的参数决定<br/>
 * 一种是fixed，seqName必须手动在库中创建好，否则seqName不存在时直接报错
 * <p>
 * 本类为dynamic模式
 * <p>
 * 一个序列有如下一些参数： seqName： 根据seqName区分不同序号服务
 * <p>
 * min: 非负，序列的初始位置，默认1
 * <p>
 * max: 大于min，序列的最大值，默认Long.MAX，约为9.2*10^18,约为920*1亿*1亿，正常足够用
 * <p>
 * step: 大于0，两个序号之间间隔，即步长，默认1
 * <p>
 * count：大于0，每次从数据库中取出到自己内存中的序号个数，默认100， 适当调大该值可减少与数据库交互次数，但也更容易造成序号浪费，比如重启，没用完的序号就浪费掉了 每次从数据库中取出的数据范围是数据库记录的当前值加上count*step
 * <p>
 * loop：在序号用完之后是否回头来再起始位置重新开始，默认false， 回头重新使用会出现序号重复，比如从1开始使用，达到最大值，再次从1开始
 *
 * @author bridgeli
 */
public class DynamicSeqService {

    private static final long DEFAULT_MIN = 1L;
    private static final long DEFAULT_MAX = Long.MAX_VALUE;
    private static final long DEFAULT_STEP = 1L;
    private static final long DEFAULT_COUNT = 100L;
    private static final boolean DEFAULT_LOOP = false;

    /**
     * 获取下一个序号ID,推荐使用
     */
    public static long nextSeq(String seqName) {
        return SequenceContext.getNextSeq(seqName, true, DEFAULT_MIN, DEFAULT_MAX, DEFAULT_STEP, DEFAULT_COUNT, DEFAULT_LOOP);
    }

    /**
     * 获取下一个序号ID, 并指定序列的启示位置
     *
     * @param seqName
     * @param startSeq 非负，起始位置，仅在seqName第一次使用初始化到数据库时有效，在seqName已经使用入库之后参数无作用
     * @return
     * @throws IllegalArgumentException
     */
    public static long nextSeq(String seqName, long startSeq) {
        return SequenceContext.getNextSeq(seqName, true, startSeq, DEFAULT_MAX, DEFAULT_STEP, DEFAULT_COUNT, DEFAULT_LOOP);
    }

    /**
     * 获取下一个序号,不推荐使用。除非你必须使用
     *
     * @param seqName ****以下参数仅在seqName第一次使用初始化到数据库时有作用，在seqName已经使用入库之后参数无作用****
     * @param min     非负，序列的初始位置
     * @param max     大于min，序列的最大值
     * @param step    大于0，两个序号之间间隔，即步长
     * @param count   大于0，每次从数据库中取出到自己内存中的序号个数，适当调大该值可减少与数据库交互次数，但也更容易造成序号浪费，比如重启，没用完的序号就浪费掉了
     * @param loop    在序号用完之后是否回头来再起始位置重新开始，回头重新使用会出现序号重复，比如从1开始使用，达到最大值，再次从1开始
     * @return
     * @throws IllegalArgumentException
     */
    public static long nextSeq(String seqName, long min, long max, long step, long count, boolean loop) {
        return SequenceContext.getNextSeq(seqName, true, min, max, step, count, loop);
    }

    public static String nextSeqWithPrefix(String prefix, String seqName) {
        if (prefix == null) {
            prefix = "";
        }
        return prefix + nextSeq(seqName);
    }

    public static String nextSeqWithDateFormat(String dateFormat, String seqName) {
        DateFormat format = new SimpleDateFormat(dateFormat);
        return nextSeqWithPrefix(format.format(new Date()), seqName);
    }

    private static final String DATE_FORMAT = "yyyyMMdd";

    public static String nextSeqWithDatePrefix(String seqName) {
        return nextSeqWithDateFormat(DATE_FORMAT, seqName);
    }

    private static final String TIME_FORMAT = "yyyyMMddHHmmss";

    public static String nextSeqWithTimePrefix(String seqName) {
        return nextSeqWithDateFormat(TIME_FORMAT, seqName);
    }

}