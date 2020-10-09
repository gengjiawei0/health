package com.itheima.health.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.health.HealthException;
import com.itheima.health.dao.SetmealDao;
import com.itheima.health.entity.PageResult;
import com.itheima.health.entity.QueryPageBean;
import com.itheima.health.pojo.CheckGroup;
import com.itheima.health.pojo.CheckItem;
import com.itheima.health.pojo.Setmeal;
import com.itheima.health.service.SetmealService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;

/**
 * Description: No Description
 * User: Eric
 */
@Service(interfaceClass = SetmealService.class)
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealDao setmealDao;

    @Autowired
    private JedisPool jedisPool;

    /**
     * 添加套餐
     * @param setmeal
     * @param checkgroupIds
     */
    @Override
    @Transactional
    public Integer add(Setmeal setmeal, Integer[] checkgroupIds) {
        // 先添加套餐
        setmealDao.add(setmeal);
        // 获取新增的套餐的id
        Integer setmealId = setmeal.getId();
        // 遍历选中的检查组id,
        if(null != checkgroupIds){
            for (Integer checkgroupId : checkgroupIds) {
                //添加套餐与检查组的关系
                setmealDao.addSetmealCheckgroup(setmealId,checkgroupId);
            }
        }
        //删除之前的套餐列表key值
        deleteKeyInSetmeal(setmealId);

        return setmealId;
    }

    /**
     * 分页查询
     * @param queryPageBean
     * @return
     */
    @Override
    public PageResult<Setmeal> findPage(QueryPageBean queryPageBean) {
        PageHelper.startPage(queryPageBean.getCurrentPage(), queryPageBean.getPageSize());
        // 有查询条件，拼接% 模糊查询
        if(!StringUtils.isEmpty(queryPageBean.getQueryString())){
            queryPageBean.setQueryString("%" + queryPageBean.getQueryString() + "%");
        }
        Page<Setmeal> page = setmealDao.findByCondition(queryPageBean.getQueryString());

        return new PageResult<Setmeal>(page.getTotal(),page.getResult());
    }

    /**
     * 通过id查询套餐信息
     * @param id
     * @return
     */
    @Override
    public Setmeal findById(int id) {
        return setmealDao.findById(id);
    }

    /**
     * 查询选中的检查组id集合
     * @param id
     * @return
     */
    @Override
    public List<Integer> findCheckGroupIdsBySetmealId(int id) {
        return setmealDao.findCheckGroupIdsBySetmealId(id);
    }

    /**
     * 更新套餐
     * @param setmeal
     * @param checkgroupIds
     */
    @Override
    @Transactional
    public void update(Setmeal setmeal, Integer[] checkgroupIds) {
        // 先更新套餐
        setmealDao.update(setmeal);
        // 删除旧关系
        setmealDao.deleteSetmealCheckGroup(setmeal.getId());
        // 添加新关系
        if(null != checkgroupIds){
            for (Integer checkgroupId : checkgroupIds) {
                setmealDao.addSetmealCheckgroup(setmeal.getId(), checkgroupId);
            }
        }
        //删除存在redis内的key
        deleteKeyInSetmeal(setmeal.getId());
    }

    /**
     * 删除套餐
     * @param id
     * @throws HealthException
     */
    @Override
    @Transactional
    public void deleteById(int id) throws HealthException {
        // 判断 是否被订单使用
        int count = setmealDao.findCountBySetmealId(id);
        // 使用了则报错
        if(count > 0) {
            throw new HealthException("该套餐已经被订单使用了，不能删除!");
        }
        // 没使用
        // 先删除套餐与检查组的关系
        setmealDao.deleteSetmealCheckGroup(id);
        // 再删除套餐
        setmealDao.deleteById(id);
        //删除存在redis内的key
        deleteKeyInSetmeal(id);
    }

    /**
     * 获取所有套餐的图片
     * @return
     */
    @Override
    public List<String> findImgs() {
        return setmealDao.findImgs();
    }

    /**
     * 查询所有的套餐
     * 将套餐存入redis
     * @return
     */
    @Override
    public List<Setmeal> findAll() {
        //生成redis
        Jedis jedis = jedisPool.getResource();
        //设置key值
        String key = "setmealLists";
        //从redis中取得key值
        String value = jedis.get(key);
        //如果key值不为空
        if (null!= value){
            //json字符串转化成list集合返回
//            List<Setmeal> setmeals = (List<Setmeal>)JSONUtils.parse(value);
            List<Setmeal> setmeals = JSONArray.parseArray(value, Setmeal.class);
            return setmeals;
        }else {
            //如果key值为空
            //查询数据库，获取后转化为字符串存入json形式redis
            List<Setmeal> setmeals = setmealDao.findAll();
//            String setmealss = JSONUtils.toJSONString(setmeals);
            String setmealss = JSON.toJSONString(setmeals);

            jedis.set(key,setmealss);
            return setmeals;
        }
    }

    /**
     *  查询套餐详情
     *  将套餐详情存入redis
     * @param id
     * @return
     */
    @Override
    public Setmeal findDetailById(int id) {
        //生成redis
        Jedis jedis = jedisPool.getResource();
        //设置key值 setmealDetail_id
        String key = "setmealDetail_"+id;
        //从redis中取得key值
        String value = jedis.get(key);
        //如果key值不为空
        if (null!=value){
            //json字符串转化成Setmeal返回
//            Setmeal setmeal1 = (Setmeal) JSONUtils.parse(setmeal);
//            Setmeal setmeal = (Setmeal) JSONArray.parse(value);
            Setmeal setmeal = JSON.parseObject(value, Setmeal.class);
            return setmeal;
        }else {
            //如果key值为空
            //查询数据库，获取后转化为字符串存入json形式redis
            Setmeal setmeal = setmealDao.findDetailById(id);
//            String setmeal2 = JSONUtils.toJSONString(setmeal);
            String setmeal1 = JSONArray.toJSONString(setmeal);
            jedis.set(key,setmeal1);
            return setmeal;
        }
    }

    @Override
    public Setmeal findDetailById2(int id) {
        return setmealDao.findDetailById2(id);
    }

    @Override
    public Setmeal findDetailById3(int id) {
        Setmeal setmeal = setmealDao.findById(id);
        // 查询套餐下的检查组
        List<CheckGroup> checkGroups = setmealDao.findCheckGroupBySetmealId(id);
        setmeal.setCheckGroups(checkGroups);
        // 查询每个检查组下的检查项
        if(checkGroups!= null && checkGroups.size() > 0){
            for (CheckGroup checkGroup : checkGroups) {
                List<CheckItem> checkItems = setmealDao.findCheckItemsByCheckGroupId(checkGroup.getId());
                checkGroup.setCheckItems(checkItems);
            }
        }
        return setmeal;
    }

    /**
     * 统计套餐预约个数
     * @return
     */
    @Override
    public List<Map<String, Object>> getSetmealReport() {
        return setmealDao.getSetmealReport();
    }

    /*当套餐增加，删除，修改时
    调用此方法
    * 删除key值*
    */
    public void deleteKeyInSetmeal(int id){
        Jedis jedis = jedisPool.getResource();
        //删除套餐列表
        jedis.del("setmealLists");
        //根据传入的id删除
        String key = "setmealDetail_"+id;

        jedis.del("setmealDetail_"+id);
    }
}
