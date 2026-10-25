package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.CommentVO;
import com.hmdp.dto.NotifyDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.INotifyService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 探店笔记评论实现
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    /** 评论分页大小 */
    private static final int PAGE_SIZE = 10;

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Resource
    private INotifyService notifyService;

    @Override
    public Result saveComment(BlogComments comment) {
        if (comment == null || comment.getBlogId() == null || StrUtil.isBlank(comment.getContent())) {
            return Result.fail("评论内容不能为空！");
        }
        Long userId = UserHolder.getUser().getId();
        comment.setUserId(userId);
        comment.setLiked(0);
        comment.setStatus(false);
        comment.setParentId(comment.getParentId() == null ? 0L : comment.getParentId());
        comment.setAnswerId(comment.getAnswerId() == null ? 0L : comment.getAnswerId());
        boolean isSuccess = save(comment);
        if (!isSuccess) {
            return Result.fail("评论失败！");
        }
        // 评论推送通知
        pushCommentNotify(comment, userId);
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryCommentsOfBlog(Long blogId, Integer current) {
        if (current == null || current < 1) {
            current = 1;
        }
        List<BlogComments> records = query()
                .eq("blog_id", blogId)
                .orderByDesc("create_time")
                .page(new Page<>(current, PAGE_SIZE))
                .getRecords();
        if (records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<CommentVO> vos = records.stream().map(c -> {
            CommentVO vo = BeanUtil.copyProperties(c, CommentVO.class);
            User user = userService.getById(c.getUserId());
            if (user != null) {
                vo.setNickName(user.getNickName());
                vo.setIcon(user.getIcon());
            }
            return vo;
        }).collect(Collectors.toList());
        return Result.ok(vos);
    }

    @Override
    public Result likeComment(Long id) {
        update().setSql("liked = liked + 1").eq("id", id).update();
        return Result.ok();
    }

    @Override
    public Result queryCommentsOfMe() {
        Long userId = UserHolder.getUser().getId();
        // 1.查询我发布的笔记id
        List<Long> blogIds = blogService.query().eq("user_id", userId).list()
                .stream().map(Blog::getId).collect(Collectors.toList());
        if (blogIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.查询这些笔记收到的评论（排除自己给自己评的）
        List<BlogComments> records = query()
                .in("blog_id", blogIds)
                .ne("user_id", userId)
                .orderByDesc("create_time")
                .last("limit 50")
                .list();
        if (records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 3.组装评论人信息与笔记标题
        List<CommentVO> vos = records.stream().map(c -> {
            CommentVO vo = BeanUtil.copyProperties(c, CommentVO.class);
            User user = userService.getById(c.getUserId());
            if (user != null) {
                vo.setNickName(user.getNickName());
                vo.setIcon(user.getIcon());
            }
            Blog blog = blogService.getById(c.getBlogId());
            if (blog != null) {
                vo.setBlogTitle(blog.getTitle());
            }
            return vo;
        }).collect(Collectors.toList());
        return Result.ok(vos);
    }

    /**
     * 评论通知：回复评论时通知被回复者，一级评论时通知笔记作者
     */
    private void pushCommentNotify(BlogComments comment, Long fromUserId) {
        Long targetUserId = null;
        if (comment.getAnswerId() != null && comment.getAnswerId() > 0) {
            BlogComments answered = getById(comment.getAnswerId());
            if (answered != null) {
                targetUserId = answered.getUserId();
            }
        } else {
            Blog blog = blogService.getById(comment.getBlogId());
            if (blog != null) {
                targetUserId = blog.getUserId();
            }
        }
        User user = userService.getById(fromUserId);
        NotifyDTO notify = new NotifyDTO();
        notify.setType(NotifyDTO.TYPE_COMMENT);
        notify.setBizId(comment.getBlogId());
        notify.setFromUserId(fromUserId);
        if (user != null) {
            notify.setFromName(user.getNickName());
            notify.setFromIcon(user.getIcon());
        }
        notify.setContent(StrUtil.sub(comment.getContent(), 0, 20));
        notify.setCreateTime(System.currentTimeMillis());
        notifyService.pushNotify(targetUserId, notify);
    }
}
