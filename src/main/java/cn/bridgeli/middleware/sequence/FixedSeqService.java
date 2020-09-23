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
 * 本类为fixed模式
 *
 * @author bridgeli
 */
public class FixedSeqService {
    public static long nextSeq(String seqName) {
        return SequenceContext.getNextSeq(seqName, false, 0, 0, 0, 0, false);
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
