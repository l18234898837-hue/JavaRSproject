package com.htht.demo.mapper;

import com.htht.common.annotation.DataColumn;
import com.htht.common.annotation.DataPermission;
import com.htht.common.core.mapper.BaseMapperPlus;
import com.htht.demo.domain.TestTree;
import com.htht.demo.domain.vo.TestTreeVo;

/**
 * 测试树表Mapper接口
 *
 * @author Lion Li
 * @date 2021-07-26
 */
@DataPermission({
    @DataColumn(key = "deptName", value = "dept_id"),
    @DataColumn(key = "userName", value = "user_id")
})
public interface TestTreeMapper extends BaseMapperPlus<TestTreeMapper, TestTree, TestTreeVo> {

}
