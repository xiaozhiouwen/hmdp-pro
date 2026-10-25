package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);

    /**
     * 查询某用户的关注数与粉丝数
     */
    Result followCount(Long userId);

    /**
     * 查询关注列表或粉丝列表
     *
     * @param userId  用户id
     * @param type    1-我关注的人，2-我的粉丝
     * @param current 页码
     */
    Result followList(Long userId, Integer type, Integer current);
}
