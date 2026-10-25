package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.NotifyDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.INotifyService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.NOTIFY_KEY;

/**
 * 消息通知实现：以 ZSet 作为每个用户的收件箱，score = 时间戳
 */
@Slf4j
@Service
public class NotifyServiceImpl implements INotifyService {

    /** 每次滚动查询的通知条数 */
    private static final int PAGE_SIZE = 10;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IBlogService blogService;

    @Resource
    private IUserService userService;

    @Override
    public void pushNotify(Long targetUserId, NotifyDTO notify) {
        if (targetUserId == null || notify == null) {
            return;
        }
        // 自己的操作不给自己发通知
        if (targetUserId.equals(notify.getFromUserId())) {
            return;
        }
        try {
            // ZADD notify:{userId} score json
            stringRedisTemplate.opsForZSet()
                    .add(NOTIFY_KEY + targetUserId, JSONUtil.toJsonStr(notify), notify.getCreateTime());
        } catch (Exception e) {
            log.error("推送通知失败：{}", e.getMessage());
        }
    }

    @Override
    public Result queryNotifyList(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = NOTIFY_KEY + userId;
        if (max == null || max == 0) {
            max = System.currentTimeMillis();
        }
        if (offset == null) {
            offset = 0;
        }
        long curMax = max;
        int curOffset = offset;
        long minTime = 0;
        int os = 1;
        int loops = 0;
        List<NotifyDTO> list = new ArrayList<>(PAGE_SIZE);
        // 循环取数：把指向已删除笔记/用户的通知清理掉，直到凑满一页或取完
        while (list.size() < PAGE_SIZE && loops++ < 10) {
            // ZREVRANGEBYSCORE key max min LIMIT offset count
            Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeByScoreWithScores(key, 0, curMax, curOffset, PAGE_SIZE - list.size());
            if (typedTuples == null || typedTuples.isEmpty()) {
                break;
            }
            for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                long time = tuple.getScore().longValue();
                if (time == minTime) {
                    os++;
                } else {
                    minTime = time;
                    os = 1;
                }
                NotifyDTO dto = JSONUtil.toBean(tuple.getValue(), NotifyDTO.class);
                if (!validateAndSync(dto)) {
                    // 脏数据（笔记/用户已不存在）：从收件箱同步移除，前端不再展示
                    stringRedisTemplate.opsForZSet().remove(key, tuple.getValue());
                    continue;
                }
                list.add(dto);
            }
            // 滚动游标推进到本轮最后一条
            curMax = minTime;
            curOffset = os;
        }
        ScrollResult result = new ScrollResult();
        result.setList(list);
        result.setMinTime(minTime);
        result.setOffset(os);
        return Result.ok(result);
    }

    /**
     * 校验通知是否有效，并实时同步触发者昵称/头像与笔记标题：
     * 触发者用户必须存在；点赞(1)/评论(3)要求笔记仍存在。
     * 快照中的昵称、头像一律以 tb_user 实时数据为准，避免信息不对应。
     */
    private boolean validateAndSync(NotifyDTO dto) {
        if (dto == null || dto.getType() == null || dto.getFromUserId() == null) {
            return false;
        }
        // 触发者必须存在，并同步最新昵称/头像
        User fromUser = userService.getById(dto.getFromUserId());
        if (fromUser == null) {
            // 用户已不存在，通知失效
            return false;
        }
        dto.setFromName(fromUser.getNickName());
        dto.setFromIcon(fromUser.getIcon());
        if (NotifyDTO.TYPE_LIKE.equals(dto.getType()) || NotifyDTO.TYPE_COMMENT.equals(dto.getType())) {
            if (dto.getBizId() == null) {
                return false;
            }
            Blog blog = blogService.getById(dto.getBizId());
            if (blog == null) {
                // 笔记已删除，通知失效
                return false;
            }
            // 点赞通知同步最新笔记标题
            if (NotifyDTO.TYPE_LIKE.equals(dto.getType())) {
                dto.setContent(StrUtil.blankToDefault(blog.getTitle(), StrUtil.sub(blog.getContent(), 0, 20)));
            }
            return true;
        }
        if (NotifyDTO.TYPE_FOLLOW.equals(dto.getType())) {
            // 关注通知的 bizId 就是触发者 id，上面已校验
            return dto.getBizId() != null;
        }
        return false;
    }
}
