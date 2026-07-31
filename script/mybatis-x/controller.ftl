package ${baseInfo.packageName};

import ${tableClass.fullClassName};
import ${serviceInterface.packageName}.${serviceInterface.fileName};
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.htht.common.core.domain.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
* @author ${author!}
* @description 针对表【${tableClass.tableName}】Controller层
* @createDate ${.now?string('yyyy-MM-dd HH:mm:ss')}
*/
@RestController
@RequestMapping("/${tableClass.shortClassName?uncap_first}")
@RequiredArgsConstructor
public class ${baseInfo.fileName} {

    private final ${serviceInterface.fileName} ${serviceInterface.fileName?uncap_first};

    /**
     * 新增
     * @param entity
     */
    @PostMapping
    public R<Void> save(@RequestBody ${tableClass.shortClassName} entity) {
        ${serviceInterface.fileName?uncap_first}.save(entity);
        return R.ok();
    }

    /**
     * 删除
     * @param id
     */
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable String id) {
        ${serviceInterface.fileName?uncap_first}.removeById(id);
        return R.ok();
    }

    /**
     * 修改
     * @param entity
     */
    @PutMapping
    public R<Void> update(@RequestBody ${tableClass.shortClassName} entity) {
        ${serviceInterface.fileName?uncap_first}.updateById(entity);
        return R.ok();
    }

    /**
     * 条件分页查询
     * @param dto
     */
    @GetMapping("/page")
    public R<Page<${tableClass.shortClassName}>> page(${tableClass.shortClassName}Dto dto) {
        return R.ok(${serviceInterface.fileName?uncap_first}.pageList(dto));
    }

}
