package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 网站内容实体，映射 site_content 表，用于存储网站各版块的可编辑内容，如标题、正文、图片链接等。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("site_content")
public class SiteContent {

    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 版块标识键，用于定位具体内容区域（如 home_banner、about_intro）
     */
    @NotBlank(message = "sectionKey 不能为空")
    private String sectionKey;

    /**
     * 版块分组，用于将多个版块归类
     */
    private String sectionGroup;

    /**
     * 内容标题
     */
    private String contentTitle;

    /**
     * 内容正文文本
     */
    private String contentText;

    /**
     * 配图URL地址
     */
    private String imageUrl;

    /**
     * 跳转链接URL地址
     */
    private String linkUrl;

    /**
     * 扩展数据（JSON格式），存放额外自定义字段
     */
    private String extraData;

    /**
     * 排序序号，数值越小越靠前
     */
    private Integer sortOrder;

    /**
     * 是否启用，1=启用，0=禁用
     */
    private Integer isActive;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
}
