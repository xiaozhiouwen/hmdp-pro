package com.hmdp.service;

import com.hmdp.dto.NotifyDTO;
import com.hmdp.dto.Result;

/**
 * 消息通知服务
 */
public interface INotifyService {

    /**
     * 给指定用户推送一条通知
     *
     * @param targetUserId 接收通知的用户id
     * @param notify       通知内容
     */
    void pushNotify(Long targetUserId, NotifyDTO notify);

    /**
     * 滚动分页查询当前用户的通知列表
     *
     * @param max    上次查询的最小时间戳，首次传 null
     * @param offset 偏移量
     */
    Result queryNotifyList(Long max, Integer offset);
}
