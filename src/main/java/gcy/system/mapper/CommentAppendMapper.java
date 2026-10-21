package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gcy.system.entity.pojo.CommentAppend;
import gcy.system.entity.vo.CommentAppendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评论追加内容的数据访问层接口。
 * 提供对 comment_append 表的查询操作，包括根据主评论ID查询追加内容、
 * 批量查询以及统计追加数量。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Mapper
public interface CommentAppendMapper extends BaseMapper<CommentAppend> {

    /**
     * 批量根据主评论ID列表查询追加内容。
     * SQL逻辑：使用 IN 子句批量匹配主评论ID，关联用户表获取用户名和头像，
     * 过滤已删除的记录，仅展示已发布或当前用户自己发布的追加内容。
     * 用户自行删除的追加内容仅对发布者本人可见。
     * 结果按追加时间升序排列。
     *
     * @param mainCommentIds 主评论ID列表
     * @param userId         当前登录用户ID，用于权限过滤
     * @return 追加内容视图对象列表，包含用户名和头像信息
     */
    @Select("<script>SELECT ca.*, u.user_name, u.icon AS user_avatar " +
            "FROM comment_append ca " +
            "LEFT JOIN user u ON ca.user_id = u.id " +
            "WHERE ca.main_comment_id IN " +
            "<foreach collection='mainCommentIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach> " +
            "AND ca.deleted = 0 " +
            "AND (ca.user_deleted = 0 OR (ca.user_deleted = 1 AND ca.user_id = #{userId})) " +
            "AND (ca.status = 1 OR ca.user_id = #{userId}) " +
            "ORDER BY ca.append_time ASC</script>")
    List<CommentAppendVO> selectByMainCommentIds(@Param("mainCommentIds") List<Long> mainCommentIds, @Param("userId") Long userId);

    /**
     * 取指定主评论下已用过的最大追评序号，没有追评时返回 0。
     * <p>
     * 刻意**不过滤 deleted**：唯一索引 uk_main_append_num 不含删除标记，被软删的记录仍占着序号。
     * 若按未删除条数（COUNT）生成序号，管理员删掉任意一条后新一轮追评就会撞唯一键，
     * 用户会一直看到「操作繁忙」且永远失败。
     */
    @Select("SELECT COALESCE(MAX(append_num), 0) FROM comment_append WHERE main_comment_id = #{mainCommentId}")
    int maxAppendNumByMainCommentId(@Param("mainCommentId") Long mainCommentId);
}
