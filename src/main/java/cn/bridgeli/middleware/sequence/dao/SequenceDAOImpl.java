package cn.bridgeli.middleware.sequence.dao;

import java.util.HashMap;
import java.util.Map;

import cn.bridgeli.middleware.sequence.core.Sequence;
import org.mybatis.spring.support.SqlSessionDaoSupport;

/**
 * @author bridgeli
 */
public class SequenceDAOImpl extends SqlSessionDaoSupport implements SequenceDAO {

    @Override
    public Sequence queryBySeqNameForUpdate(String seqName) {
        Sequence sequenceRangeDB = (Sequence) getSqlSession().selectOne("Sequence.queryBySeqName", seqName);
        if (null != sequenceRangeDB && (sequenceRangeDB.getStep() < 1 || sequenceRangeDB.getCount() < 1)) {
            throw new RuntimeException("序号生成配置异常.name=" + seqName);
        }
        return sequenceRangeDB;
    }

    @Override
    public int update(String seqName, long current) {
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("current", current);
        param.put("name", seqName);
        return getSqlSession().update("Sequence.update", param);
    }

    @Override
    public int insert(Sequence sequence) {
        if (null != sequence && (sequence.getStep() < 1 || sequence.getCount() < 1)) {
            throw new RuntimeException("序号生成配置异常.name=" + sequence.getName());
        }
        return getSqlSession().insert("Sequence.insert", sequence);
    }

}
