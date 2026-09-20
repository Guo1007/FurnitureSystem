package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体，映射 coupon 表。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("coupon")
public class Coupon {

    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 券名称
     */
    private String name;

    /**
     * 券类型：1-满减券，2-折扣券，3-无门槛券
     */
    private Integer type;

    /**
     * 使用门槛金额（0表示无门槛）
     */
    private BigDecimal minThreshold;

    /**
     * 减免金额（满减/无门槛券使用）
     */
    private BigDecimal amount;

    /**
     * 折扣率（折扣券使用，如0.80表示8折）
     */
    private BigDecimal discount;

    /**
     * 折扣券最高优惠上限（可空）
     */
    private BigDecimal capAmount;

    /**
     * 适用范围：0-全场，1-按分类
     */
    private Integer scope;

    /**
     * scope=1时的家具分类ID（可空）
     */
    private Long typeId;

    /**
     * 发放总量（NULL表示不限）
     */
    private Integer totalCount;

    /**
     * 每人限领数
     */
    private Integer perUserLimit;

    /**
     * 领取开始时间（NULL表示不限）
     */
    private LocalDateTime claimStart;

    /**
     * 领取结束时间（NULL表示不限）
     */
    private LocalDateTime claimEnd;

    /**
     * 有效期模式：1-固定有效期，2-领取后N天
     */
    private Integer validType;

    /**
     * 固定有效期开始（validType=1时使用）
     */
    private LocalDateTime validStart;

    /**
     * 固定有效期结束（validType=1时使用）
     */
    private LocalDateTime validEnd;

    /**
     * 领取后有效天数（validType=2时使用）
     */
    private Integer validDays;

    /**
     * 领取人群：0-不限，1-新用户，2-老用户
     */
    private Integer targetType;

    /**
     * 新/老用户判定天数阈值
     */
    private Integer targetDays;

    /**
     * 状态：0-停用，1-启用
     */
    private Integer status;

    /**
     * 逻辑删除（0未删/1已删）
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}