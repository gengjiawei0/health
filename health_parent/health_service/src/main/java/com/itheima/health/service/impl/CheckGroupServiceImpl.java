package com.itheima.health.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.health.HealthException;
import com.itheima.health.dao.CheckGroupDao;
import com.itheima.health.entity.PageResult;
import com.itheima.health.entity.QueryPageBean;
import com.itheima.health.pojo.CheckGroup;
import com.itheima.health.service.CheckGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

/**
 * Description: No Description
 * User: Eric
 */
@Service(interfaceClass = CheckGroupService.class)
public class CheckGroupServiceImpl implements CheckGroupService {

    @Autowired
    private CheckGroupDao checkGroupDao;

    @Autowired
    private JedisPool jedisPool;

    /**
     * 添加检查组
     * @param checkGroup
     * @param checkitemIds
     */
    @Override
    @Transactional
    public void add(CheckGroup checkGroup, Integer[] checkitemIds) {
        // 添加检查组
        checkGroupDao.add(checkGroup);
        // 获取检查组id
        Integer checkGroupId = checkGroup.getId();
        // 循环遍历选中检查项id，
        if(null != checkitemIds){
            for (Integer checkitemId : checkitemIds) {
                //添加检查组与检查项的关系
                checkGroupDao.addCheckGroupCheckItem(checkGroupId,checkitemId);
            }
        }
    }

    /**
     * 分页查询
     * @param queryPageBean
     * @return
     */
    @Override
    public PageResult<CheckGroup> findPage(QueryPageBean queryPageBean) {
        PageHelper.startPage(queryPageBean.getCurrentPage(), queryPageBean.getPageSize());
        // 有条件，则要模糊查询
        if(!StringUtils.isEmpty(queryPageBean.getQueryString())){
            // 拼接 %
            queryPageBean.setQueryString("%" + queryPageBean.getQueryString() + "%");
        }
        // 条件查询
        Page<CheckGroup> page = checkGroupDao.findPage(queryPageBean.getQueryString());
        PageResult<CheckGroup> pageResult = new PageResult<CheckGroup>(page.getTotal(), page.getResult());
        return pageResult;
    }

    /**
     * 通过id查询
     * @param id
     * @return
     */
    @Override
    public CheckGroup findById(int id) {
        return checkGroupDao.findById(id);
    }

    /**
     * 通过检查组id查询选中的检查项id集合
     * @param id
     * @return
     */
    @Override
    public List<Integer> findCheckItemIdsByCheckGroupId(int id) {
        return checkGroupDao.findCheckItemIdsByCheckGroupId(id);
    }

    /**
     * 修改检查组
     * @param checkGroup
     * @param checkitemIds
     */
    @Override
    @Transactional
    public void update(CheckGroup checkGroup, Integer[] checkitemIds) {
        // 先更新检查组信息
        checkGroupDao.update(checkGroup);
        // 删除旧关系
        checkGroupDao.deleteCheckGroupCheckItem(checkGroup.getId());
        // 添加新关系
        if(null != checkitemIds){
            for (Integer checkitemId : checkitemIds) {
                //添加检查组与检查项的关系
                checkGroupDao.addCheckGroupCheckItem(checkGroup.getId(),checkitemId);
            }
        }
        //删除套餐在redis中储存的key
        deleteKeyInCheckGroup(checkGroup.getId());
    }

    /**
     * 通过id删除
     * @param id
     * @throws HealthException
     */
    @Override
    @Transactional
    public void deleteById(int id) throws HealthException {
        // 判断是被套餐使用了
        int count = checkGroupDao.findSetmealCountByCheckGroupId(id);
        // count>0使用了
        if(count > 0){
            throw new HealthException("访检查组已经被套餐使用了，不能删除");
        }
        // 未被使用
        // 先删除检查组与检查项关系
        checkGroupDao.deleteCheckGroupCheckItem(id);
        // 再删除检查组
        checkGroupDao.deleteById(id);
    }

    /**
     * 查询所有
     * @return
     */
    @Override
    public List<CheckGroup> findAll() {
        return checkGroupDao.findAll();
    }

    /*
    *检查组修改时
    * 调用删除key值
     *  */
    public void deleteKeyInCheckGroup(int id) {
        Jedis jedis = jedisPool.getResource();
        //根据传入id查询关联表
        List<Integer> setmealIds = checkGroupDao.findSetmealIdByGroupId(id);
        //如果list不为null
        if (setmealIds!=null&&setmealIds.size()>0){
            //遍历
            for (Integer sid : setmealIds) {
                //根据传入的id删除setmeal详情
                jedis.del("setmealDetail_" + sid);
            }
        }

    }
}
