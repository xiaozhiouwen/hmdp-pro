package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.dto.UserDTO;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 启动时把店铺坐标预热到 Redis GEO，按 typeId 分组存放到 shop:geo:{typeId}
     * 容错：Redis 不可用时只记录日志，不阻断应用启动
     */
    @PostConstruct
    public void loadShopData() {
        try {
            List<Shop> list = list();
            Map<Long, List<Shop>> typeMap = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
            for (Map.Entry<Long, List<Shop>> entry : typeMap.entrySet()) {
                String key = SHOP_GEO_KEY + entry.getKey();
                List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(entry.getValue().size());
                for (Shop shop : entry.getValue()) {
                    locations.add(new RedisGeoCommands.GeoLocation<>(
                            shop.getId().toString(), new Point(shop.getX(), shop.getY())));
                }
                stringRedisTemplate.opsForGeo().add(key, locations);
            }
            log.info("店铺GEO数据预热完成，共 {} 个分类", typeMap.size());
        } catch (Exception e) {
            log.error("店铺GEO数据预热失败，不影响应用启动：{}", e.getMessage());
        }
    }

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7.统计商户 UV：HyperLogLog 以 userId 为元素去重计数
        recordUV(id);
        // 8.返回
        return Result.ok(shop);
    }

    /**
     * 记录商户访问用户，PFADD shop:uv:{shopId} userId
     */
    private void recordUV(Long shopId) {
        try {
            UserDTO user = UserHolder.getUser();
            if (user == null) {
                return;
            }
            stringRedisTemplate.opsForHyperLogLog().add(SHOP_UV_KEY + shopId, user.getId().toString());
        } catch (Exception e) {
            // 统计失败不影响主流程
            log.error("记录商户UV失败：{}", e.getMessage());
        }
    }

    @Override
    public Result queryShopUV(Long shopId) {
        Long uv = stringRedisTemplate.opsForHyperLogLog().size(SHOP_UV_KEY + shopId);
        return Result.ok(uv == null ? 0L : uv);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String sortBy) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEORADIUS key x y 5000 m WITHDIST COUNT end
                .radius(
                        key,
                        new Circle(new Point(x, y), new Distance(5000, RedisGeoCommands.DistanceUnit.METERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop：默认按距离(GEO返回顺序)，也可按人气/评分排序
        String sortColumn = resolveSortColumn(sortBy);
        List<Shop> shops;
        if (sortColumn != null) {
            shops = query().in("id", ids).orderByDesc(sortColumn).list();
        } else {
            String idStr = StrUtil.join(",", ids);
            shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        }
        for (Shop shop : shops) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }
        // 6.返回
        return Result.ok(shops);
    }

    /**
     * 排序字段白名单，避免直接拼接前端参数导致 SQL 注入
     *
     * @param sortBy 前端排序类型：sold-人气、score-评分、comments-评论数，空则按距离
     * @return 数据库排序字段，null 表示按距离排序
     */
    private String resolveSortColumn(String sortBy) {
        if (StrUtil.isBlank(sortBy)) {
            return null;
        }
        switch (sortBy) {
            case "sold":
                return "sold";
            case "score":
                return "score";
            case "comments":
                return "comments";
            default:
                return null;
        }
    }
}
