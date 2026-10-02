package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.ManagedInputFileEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

public interface ManagedInputFileMyBatisMapper extends BaseMapper<ManagedInputFileEntity> {
    @Select("SELECT * FROM rs_managed_input_file WHERE workspace_key=#{workspaceKey} AND sys_file_id=#{sysFileId}")
    ManagedInputFileEntity selectByWorkspaceAndSysFile(@Param("workspaceKey") String workspaceKey, @Param("sysFileId") long sysFileId);

    @Insert("INSERT INTO rs_managed_input_file(workspace_key,sys_file_id,file_path_snapshot,checksum,size_bytes,status) "
        + "VALUES(#{workspaceKey},#{sysFileId},#{path},#{checksum},#{sizeBytes},'MANAGED') ON CONFLICT DO NOTHING")
    int insertManaged(@Param("workspaceKey") String workspaceKey, @Param("sysFileId") long sysFileId,
                      @Param("path") String path, @Param("checksum") String checksum, @Param("sizeBytes") long sizeBytes);

    @Update("UPDATE rs_managed_input_file SET last_seen_at=CURRENT_TIMESTAMP WHERE managed_file_id=#{id}")
    int touch(@Param("id") long id);
}
