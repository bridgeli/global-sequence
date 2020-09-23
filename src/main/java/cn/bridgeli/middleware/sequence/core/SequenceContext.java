package cn.bridgeli.middleware.sequence.core;

import cn.bridgeli.middleware.sequence.dao.SequenceDAO;
import cn.bridgeli.middleware.sequence.dao.SequenceDAOImpl;
import lombok.Setter;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 分两种模式，
 * 一种是dynamic，即seqName不存在时自动在数据库中创建，sequence对象的属性由getNextSeq方法的参数决定
 * 一种是fixed，seqName必须手动在库中创建好，否则seqName不存在时直接报错
 *
 * @author bridgeli
 */
public class SequenceContext implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger("GLOBAL-SEQUENCE");

    private static final String DYNAMIC_MODE_PREFIX = "_dynamic_";
    private static SequenceDAO sequenceDAO;
    private static TransactionTemplate transactionTemplate;

    private static AtomicBoolean initialized = new AtomicBoolean(false);
    private static Map<String, Sequence> cache = new ConcurrentHashMap<String, Sequence>();

    private static final int DEFAULT_MAX_CACHE_SIZE = 50000;
    private static final int DEFAULT_EVICTION_ANALYSIS_THRESHOLD = 40000;
    private static final int DEFAULT_SURVIVOR_SIZE_AFTER_EVICTION = 30000;

    /**
     * 不同序列的缓存大小 maxCacheSize 缓存最大值 evictionAnalysisThreshold 缓存个数超过该值开始记录序列的使用时间 survivorSizeAfterEviction 缓存个数达到最大值后，根据记录序列的最后使用时间排序，清除缓存中最旧序列（即最后使用时间最早），存留个数
     * <p>
     * 三者关系为 maxCacheSize > evictionAnalysisThreshold > survivorSizeAfterEviction > 0
     * <p>
     * 一般不用设置
     */
    private static int maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
    private static int evictionAnalysisThreshold = DEFAULT_EVICTION_ANALYSIS_THRESHOLD;
    private static int survivorSizeAfterEviction = DEFAULT_SURVIVOR_SIZE_AFTER_EVICTION;

    @Setter
    private DataSource dataSource;

    @Override
    public void afterPropertiesSet() throws Exception {
        initialize(dataSource);
    }

    public static void setCacheSize(int _maxCacheSize, int _evictionAnalysisThreshold, int _survivorSizeAfterEviction) {
        if (!(_maxCacheSize > _evictionAnalysisThreshold && _evictionAnalysisThreshold > _survivorSizeAfterEviction && _survivorSizeAfterEviction > 0)) {
            throw new IllegalArgumentException("缓存参数关系应该为_maxCacheSize>_evictionAnalysisThreshold>_survivorSizeAfterEviction>0");
        }
        maxCacheSize = _maxCacheSize;
        evictionAnalysisThreshold = _evictionAnalysisThreshold;
        survivorSizeAfterEviction = _survivorSizeAfterEviction;
        LOGGER.info(String.format(
                "设置缓存大小,maxCacheSize:%d,evictionAnalysisThreshold:%d,survivorSizeAfterEviction:%d,原大小为：maxCacheSize:%d,evictionAnalysisThreshold:%d,survivorSizeAfterEviction:%d",
                _maxCacheSize, _evictionAnalysisThreshold, _survivorSizeAfterEviction, maxCacheSize, evictionAnalysisThreshold, survivorSizeAfterEviction));
    }

    public static boolean initialize(DataSource dataSource) throws Exception {
        notNull(dataSource, "参数dataSource不能为空！");

        if (initialized.compareAndSet(false, true)) {
            SqlSessionFactoryBean sequenceSqlSessionFactory = new SqlSessionFactoryBean();
            sequenceSqlSessionFactory.setDataSource(dataSource);
            sequenceSqlSessionFactory.setConfigLocation(new ClassPathResource("mybatis/sequence-configuration.xml"));
            sequenceSqlSessionFactory.setMapperLocations(new ClassPathResource[]{new ClassPathResource("mybatis/sequence/sequence.xml")});

            DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
            transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setName("sequenceTransaction");
            transactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");

            sequenceDAO = new SequenceDAOImpl();
            ((SequenceDAOImpl) sequenceDAO).setSqlSessionFactory(sequenceSqlSessionFactory.getObject());
            LOGGER.info(String.format("全局序列号生成组件初始化完成！DataSource为:%s,maxCacheSize:%d,evictionAnalysisThreshold:%d,survivorSizeAfterEviction:%d", dataSource,
                    maxCacheSize, evictionAnalysisThreshold, survivorSizeAfterEviction));
            return true;
        } else {
            LOGGER.warn(
                    String.format("全局序列号生成组件已经初始化,忽略本次初始化，忽略本次参数如下：DataSource为:%s,maxCacheSize:%d,evictionAnalysisThreshold:%d,survivorSizeAfterEviction:%d",
                            dataSource, maxCacheSize, evictionAnalysisThreshold, survivorSizeAfterEviction));
            return false;
        }
    }

    public static long getNextSeq(String seqName, boolean dynamic, long min, long max, long step, long count, boolean isLoop) {
        if (dynamic) {
            isTrue(min >= 0, "最小值为负");
            isTrue(max > min, "最大值小于最小值");
            isTrue(step > 0, "步长为负或0");
            isTrue(count > 0, "一次性获取个数为负或0");
        }
        while (!initialized.get()) {
            LOGGER.warn("等待SequenceContext被spring容器初始化，或者请检查SequenceContext是否被配置为Spring bean.");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
        String seqFullName = getSeqName(seqName, dynamic);
        if (getSequenceFromCache(seqFullName) == null) {
            initSequence(seqFullName, dynamic, min, max, step, count, isLoop);
        }
        return getSequenceNum(seqFullName);
    }

    private static long getSequenceNum(final String seqName) {
        final Sequence sequence = getSequenceFromCache(seqName);
        if (sequence == null) {
            throw new SequenceException("序号生成服务未知异常");
        }
        // 本地已经用完需要取数据库取
        if (sequence.getCurrent() >= sequence.getMax()) {
            updateFromDBAndUpdateDB(sequence);
        }
        try {
            return sequence.getCurrentAndIncrementStep();
        } catch (CASEqualsException e) {
            return getSequenceNum(seqName);
        }
    }

    private static Sequence getSequenceFromCache(String seqName) {
        Sequence sequence = cache.get(seqName);
        if (sequence != null && cache.size() > evictionAnalysisThreshold) {
            sequence.setLastUpdate(System.currentTimeMillis());
        }
        return sequence;
    }

    /**
     * 从数据库更新序号范围并更新本地序号范围并更新数据库的current
     */
    private static void updateFromDBAndUpdateDB(final Sequence sequence) {

        synchronized (sequence) {
            if (sequence.getCurrent() < sequence.getMax()) {
                return;
            }
            transactionTemplate.execute(new TransactionCallback<Sequence>() {
                @Override
                public Sequence doInTransaction(TransactionStatus status) {

                    Sequence seqFromDB = sequenceDAO.queryBySeqNameForUpdate(sequence.getName());// 锁表
                    sequence.updateFromDB(seqFromDB);// 并同步本地current
                    sequenceDAO.update(sequence.getName(), sequence.getMax());// 本地最大值更新回数据库等待其他客户端获取下一段序号范围
                    return sequence;
                }
            });
        }

    }

    private static synchronized void initSequence(final String seqName, final boolean dynamic, final long min, final long max, final long step,
                                                  final long count, final boolean isLoop) {
        if (getSequenceFromCache(seqName) != null) {
            return;
        }
        Sequence sequence = transactionTemplate.execute(new TransactionCallback<Sequence>() {

            @Override
            public Sequence doInTransaction(TransactionStatus status) {
                Sequence seqFromDB = sequenceDAO.queryBySeqNameForUpdate(seqName);
                if (seqFromDB == null) {
                    if (dynamic) {
                        seqFromDB = new Sequence();
                        seqFromDB.setName(seqName);
                        seqFromDB.setCount(count);
                        if (isLoop) {
                            seqFromDB.setLoop(Sequence.LOOP_YES);
                        } else {
                            seqFromDB.setLoop(Sequence.LOOP_NO);
                        }
                        seqFromDB.setMax(max);
                        seqFromDB.setMin(min);
                        seqFromDB.setStep(step);
                        seqFromDB.setCurrent(min);
                        try {
                            sequenceDAO.insert(seqFromDB);
                        } catch (DuplicateKeyException e) {
                            // 和谐掉主键重复异常,重新查询一次
                            seqFromDB = sequenceDAO.queryBySeqNameForUpdate(seqName);
                        }
                    } else {
                        throw new SequenceException("fixed模式下,数据库中序号数据不存在：" + seqName);
                    }
                }
                Sequence seq = new Sequence();
                seq.setName(seqName);
                seq.updateFromDB(seqFromDB);
                // 本地最大值更新回数据库等待其他客户端获取下一段序号范围
                sequenceDAO.update(seq.getName(), seq.getMax());
                return seq;
            }

        });
        // 清理不活跃sequence对象
        if (cache.size() >= maxCacheSize) {
            TreeSet<Sequence> analysisTree = new TreeSet<Sequence>(cache.values());
            while (analysisTree.size() > survivorSizeAfterEviction) {
                Sequence seq = analysisTree.pollFirst();
                cache.remove(seq.getName());
            }
        }
        sequence.setLastUpdate(System.currentTimeMillis());
        cache.put(seqName, sequence);
    }

    private static String getSeqName(String seqName, boolean dynamic) {
        return dynamic ? DYNAMIC_MODE_PREFIX + seqName : seqName;
    }

    private static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void isTrue(boolean result, String message) {
        if (!result) {
            throw new IllegalArgumentException(message);
        }
    }
}
