package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import gcy.system.entity.pojo.GoodsComment;
import gcy.system.entity.vo.CommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品评论Mapper接口。
 * <p>
 * 提供商品评论相关的数据库查询操作，包括按商品ID、订单ID等条件查询评论信息。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Mapper
public interface GoodsCommentMapper extends BaseMapper<GoodsComment> {

    /**
     * 分页查询指定商品的评论列表。
     * <p>
     * SQL逻辑：关联用户表获取评论者头像和用户名，查询未删除的评论。
     * 当前用户可以看到所有审核通过的评论以及自己发布的评论（包括已删除的）。
     * </p>
     *
     * @param goodsId 商品ID
     * @param userId  当前登录用户ID
     * @param page    分页对象
     * @return 分页的评论视图对象列表
     */
    @Select("SELECT gc.*, u.user_name, u.icon AS user_avatar " +
            "FROM goods_comment gc " +
            "LEFT JOIN user u ON gc.user_id = u.id " +
            "WHERE gc.goods_id = #{goodsId} AND gc.deleted = 0 " +
            "AND (gc.user_deleted = 0 OR (gc.user_deleted = 1 AND gc.user_id = #{userId})) " +
            "AND gc.status = 1 " +
            "ORDER BY gc.create_time DESC")
    Page<CommentVO> selectCommentsByGoodsId(@Param("goodsId") Long goodsId, @Param("userId") Long userId, Page<CommentVO> page);

    /**
     * 根据订单ID、用户ID和商品ID查询单条评论记录。
     * <p>
     * SQL逻辑：从goods_comment表中查询满足订单、用户、商品三个条件且未被删除的评论。
     * </p>
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 匹配的商品评论实体，未找到则返回null
     */
    @Select("SELECT id, order_id, order_item_id, goods_id, user_id, score, content, img_url, video_url, is_anonym, status, has_append, latest_append_time, create_time, deleted, user_deleted FROM goods_comment WHERE order_id = #{orderId} AND user_id = #{userId} AND goods_id = #{goodsId} AND deleted = 0 AND user_deleted = 0")
    GoodsComment selectByOrderAndGoods(@Param("orderId") Long orderId, @Param("userId") Long userId, @Param("goodsId") Long goodsId);

    /**
     * 根据订单ID查询该订单下的所有评论。
     * <p>
     * SQL逻辑：关联用户表获取评论者头像和用户名。当前用户可以看到所有审核通过的评论
     * 以及自己发布的评论（包括已删除的），并按创建时间倒序排列。
     * </p>
     *
     * @param orderId 订单ID
     * @param userId  当前登录用户ID
     * @return 评论视图对象列表
     */
    @Select("SELECT gc.*, u.user_name, u.icon AS user_avatar " +
            "FROM goods_comment gc " +
            "LEFT JOIN user u ON gc.user_id = u.id " +
            "WHERE gc.order_id = #{orderId} " +
            "AND (gc.deleted = 0 OR (gc.deleted = 1 AND gc.user_id = #{userId})) " +
            "AND (gc.user_deleted = 0 OR (gc.user_deleted = 1 AND gc.user_id = #{userId})) " +
            "AND gc.status = 1 " +
            "ORDER BY gc.create_time DESC")
    List<CommentVO> selectCommentsByOrderId(@Param("orderId") Long orderId, @Param("userId") Long userId);
}
