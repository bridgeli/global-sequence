package cn.bridgeli.middleware.sequence.dao;

import cn.bridgeli.middleware.sequence.core.Sequence;

/**
 * @author bridgeli
 */
public interface SequenceDAO {

    /**
     * 通过Seq类型查询Seq配置
     *
     * @param seqName
     * @return
     */
    Sequence queryBySeqNameForUpdate(String seqName);

    /**
     * 更新当前序号
     */
    int update(String seqName, long current);

    /**
     * 新建序号配置
     *
     * @param sequence
     * @return
     */
    int insert(Sequence sequence);
}
