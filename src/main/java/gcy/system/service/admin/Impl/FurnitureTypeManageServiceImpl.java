package gcy.system.service.admin.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminFurnitureTypeFormDTO;
import gcy.system.entity.pojo.Furniture;
import gcy.system.entity.pojo.FurnitureType;
import gcy.system.exception.BusinessException;
import gcy.system.mapper.FurnitureMapper;
import gcy.system.mapper.FurnitureTypeMapper;
import gcy.system.service.admin.IFurnitureTypeManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static gcy.system.utils.RedisConstants.CACHE_FURNITURE_TYPE_KEY;

/**
 * 家具类型管理服务实现类。
 * <p>
 * 实现了 {@link IFurnitureTypeManageService} 接口中定义的家具类型增删改查功能。
 * 使用 MyBatis-Plus 的 {@link ServiceImpl} 作为基础持久层支持，
 * 通过 Hutool 工具类完成 DTO 与实体之间的转换，并对关键操作进行事务控制。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Service
@RequiredArgsConstructor
public class FurnitureTypeManageServiceImpl extends ServiceImpl<FurnitureTypeMapper, FurnitureType> implements IFurnitureTypeManageService {

    private final FurnitureMapper furnitureMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 清除用户端家具类型列表缓存，使增删改立即生效。
     * <p>
     * 删除动作推迟到事务提交之后：若在提交前删除，并发请求会在缓存未命中时
     * 读取到「尚未提交」的旧数据并回写缓存，导致提交后缓存中长期保留旧值。
     * 若当前不在事务中则立即删除。
     * </p>
     */
    private void clearTypeCache() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stringRedisTemplate.delete(CACHE_FURNITURE_TYPE_KEY);
                }
            });
        } else {
            stringRedisTemplate.delete(CACHE_FURNITURE_TYPE_KEY);
        }
    }

    /**
     * 添加新的家具类型。
     * <p>
     * 将前端传入的 {@link AdminFurnitureTypeFormDTO} 表单数据通过 {@link BeanUtil#toBean(Object, Class)}
     * 转换为 {@link FurnitureType} 实体对象，然后调用 MyBatis-Plus 的 {@code save} 方法将数据持久化到数据库。
     * 整个操作在一个事务中执行，若保存失败则自动回滚。
     * </p>
     *
     * @param dto 包含待添加家具类型信息的表单数据传输对象，含类型名称、描述等字段
     * @return {@link Result} 操作结果，成功时返回 "添加成功" 提示，失败时返回 "添加失败" 提示
     */
    @Override
    @Transactional
    public Result addFurnitureType(AdminFurnitureTypeFormDTO dto) {
        FurnitureType type = BeanUtil.toBean(dto, FurnitureType.class);
        boolean success = this.save(type);
        if (success) {
            clearTypeCache();
        }
        return success ? Result.okMsg("添加成功") : Result.fail("添加失败");
    }

    /**
     * 编辑已有的家具类型。
     * <p>
     * 首先校验 DTO 中的主键 ID 是否为空，若为空则直接返回失败结果。
     * 校验通过后，将 DTO 数据转换为 {@link FurnitureType} 实体对象，
     * 通过 MyBatis-Plus 的 {@code updateById} 方法按主键更新数据库记录。
     * 整个操作在一个事务中执行。
     * </p>
     *
     * @param dto 包含待更新家具类型信息的表单数据传输对象，其中 {@code id} 字段必须不为空
     * @return {@link Result} 操作结果，成功时返回 "更新成功" 提示，失败时返回 "更新失败" 提示；
     * 若 ID 为空则返回 "ID 不能为空" 错误信息
     */
    @Override
    @Transactional
    public Result editFurnitureType(AdminFurnitureTypeFormDTO dto) {
        if (dto.getId() == null) {
            return Result.fail("ID 不能为空");
        }
        FurnitureType type = BeanUtil.toBean(dto, FurnitureType.class);
        boolean success = this.updateById(type);
        if (success) {
            clearTypeCache();
        }
        return success ? Result.okMsg("更新成功") : Result.fail("更新失败");
    }

    /**
     * 删除指定的家具类型。
     * <p>
     * 在删除之前，先通过 {@link FurnitureMapper} 查询关联该分类的商品数量。
     * 若存在关联商品（数量大于 0），则抛出 {@link BusinessException} 异常，
     * 提示用户先将关联商品迁移或删除后再操作，防止产生孤立数据。
     * 若没有关联商品，则通过 MyBatis-Plus 的 {@code removeById} 方法按主键删除记录。
     * 整个操作在一个事务中执行。
     * </p>
     *
     * @param id 要删除的家具类型的唯一标识ID
     * @return {@link Result} 操作结果，成功时返回 "删除成功" 提示，失败时返回 "删除失败" 提示
     * @throws BusinessException 当该分类下存在关联的商品时抛出，异常消息包含关联商品的数量
     */
    @Override
    @Transactional
    public Result deleteFurnitureType(Long id) {
        // 检查是否有家具关联此分类
        Long furnitureCount = furnitureMapper.selectCount(
                new LambdaQueryWrapper<Furniture>().eq(Furniture::getTypeId, id));
        if (furnitureCount > 0) {
            throw new BusinessException("该分类下有 " + furnitureCount + " 件商品，请先迁移或删除商品后再操作");
        }
        boolean success = this.removeById(id);
        if (success) {
            clearTypeCache();
        }
        return success ? Result.okMsg("删除成功") : Result.fail("删除失败");
    }

    /**
     * 根据ID获取单个家具类型的详细信息。
     * <p>
     * 通过 MyBatis-Plus 的 {@code getById} 方法查询指定主键的 {@link FurnitureType} 记录。
     * 若查询结果为空，则返回带有 "数据不存在" 错误信息的失败结果；
     * 若查询到数据，则通过 {@link BeanUtil#toBean(Object, Class)} 将实体转换为
     * {@link AdminFurnitureTypeFormDTO} 并封装到成功结果中返回。
     * </p>
     *
     * @param id 要查询的家具类型的唯一标识ID
     * @return {@link Result} 操作结果，成功时包含家具类型的详细信息；若数据不存在则返回相应错误状态
     */
    @Override
    public Result getFurnitureTypeById(Long id) {
        FurnitureType type = this.getById(id);
        if (type == null) {
            return Result.fail("数据不存在");
        }
        AdminFurnitureTypeFormDTO dto = BeanUtil.toBean(type, AdminFurnitureTypeFormDTO.class);
        return Result.ok(dto);
    }

    /**
     * 分页查询家具类型列表。
     * <p>
     * 使用 MyBatis-Plus 的 {@link Page} 分页对象构造分页查询条件。
     * 当传入的 {@code typeName} 参数不为空时，通过 {@link LambdaQueryWrapper}
     * 构建对 {@link FurnitureType#getName()} 字段的模糊匹配（LIKE）条件。
     * 执行分页查询后，若结果集中总记录数为 0，则返回 "暂未发现任何数据！" 的错误提示；
     * 否则返回包含分页数据的结果。
     * </p>
     *
     * @param current  当前页码，从1开始，用于指定查询第几页的数据
     * @param size     每页显示的记录条数，用于控制分页大小
     * @param typeName 家具类型名称，用于模糊搜索匹配；可为空或空白，为空时查询全部记录
     * @return {@link Result} 操作结果，成功时包含分页后的家具类型列表及分页信息（总记录数、总页数等）；
     * 若未查询到任何数据则返回错误状态
     */
    @Override
    public Result getFurnitureTypeList(Integer current, Integer size, String typeName) {
        Page<FurnitureType> page = new Page<>(current, size);
        LambdaQueryWrapper<FurnitureType> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(typeName)) {
            queryWrapper.like(FurnitureType::getName, typeName);
        }
        Page<FurnitureType> resultPage = page(page, queryWrapper);
        if (resultPage.getTotal() == 0) {
            return Result.fail("暂未发现任何数据！");
        }
        return Result.ok(resultPage);
    }
}