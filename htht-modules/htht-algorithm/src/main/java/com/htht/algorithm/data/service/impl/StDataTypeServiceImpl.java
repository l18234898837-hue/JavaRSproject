package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.algorithm.data.domain.BusCatalogInfo;
import com.htht.algorithm.data.domain.dto.SatelliteDto;
import com.htht.algorithm.data.domain.vo.*;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.domain.StDataType;
import com.htht.algorithm.data.mapper.StDataTypeMapper;
import com.htht.algorithm.data.service.StDataTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.R;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 存储遥感数据类型定义 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-07-24
 */
@Service
@RequiredArgsConstructor
public class StDataTypeServiceImpl extends ServiceImpl<StDataTypeMapper, StDataType> implements StDataTypeService {


    private final IBusCatalogInfoService busCatalogInfoService;

    @Override
    public List<StDataType> buildTree() {
        List<StDataType> catalogInfoList = this.list();
        //将菜单List转Map，菜单ID为key，菜单对象为value
        Map<Long, StDataType> catalogMap = catalogInfoList.stream().collect(
            Collectors.toMap(StDataType::getId, Function.identity(), (key1, key2) -> key2)
        );
        List<StDataType> regionTree = new ArrayList<>();
        catalogMap.entrySet().stream().forEach(entry -> {
            if (entry.getValue().getLevel() > 1) {
                StDataType parentCatalog = catalogMap.get(entry.getValue().getPid());
                List<StDataType> children = parentCatalog.getChildren();
                children.add(entry.getValue());
                children.sort(Comparator.comparing(StDataType::getId));
            } else if (entry.getValue().getLevel() == 1) {
                regionTree.add(entry.getValue());
            }
        });
        regionTree.sort(Comparator.comparing(StDataType::getId));
        return regionTree;
    }

    @Override
    public String check() {
        List<Long> falseList=new ArrayList<>();
        List<StDataType> stDataTypeList = list();
        for (StDataType stDataType : stDataTypeList) {
            if(!check(stDataType)){
                falseList.add(stDataType.getId());
            }
        }
        if(falseList.isEmpty()){
            return "目录绑定没有问题";
        }
        return "目录绑定有问题的数据类型"+falseList;
    }

    private boolean check(StDataType stDataType ){
        Long productCatalogId = stDataType.getProductCatalogId();
        Long calculateCatalogId = stDataType.getCalculateCatalogId();
        if(productCatalogId!=null){
            BusCatalogInfo busCatalogInfo = busCatalogInfoService.getById(productCatalogId);
            if (!busCatalogInfo.getName().equals(stDataType.getName())) {
                return false;
            }
        }
        if(calculateCatalogId!=null){
            BusCatalogInfo busCatalogInfo = busCatalogInfoService.getById(calculateCatalogId);
            if (!busCatalogInfo.getName().equals(stDataType.getName())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean add(StDataType stDataType) {
        stDataType.setLevel(1);
        return save(stDataType);
    }

    @Override
    public boolean addChild(DataTypeChildVo dataTypeChildVo) {
        long pid = dataTypeChildVo.getPid();
        StDataType byIdGetParent = getById(pid);
        if(byIdGetParent==null){
            throw new GlobalException("并不存在这个父数据节点");
        }
        StDataType stDataType = new StDataType().setPid(pid).setName(dataTypeChildVo.getName()).setLevel(byIdGetParent.getLevel()+1);
        Long calculateCatalogId = byIdGetParent.getCalculateCatalogId();
        if(calculateCatalogId!=null){
            BusCatalogInfo busCatalogInfo = busCatalogInfoService.getById(calculateCatalogId);
            if(busCatalogInfo==null){
                throw new GlobalException(byIdGetParent.getName()+"的计算目录不存在");
            }
            BusCatalogInfo calculateCatalogChild=new BusCatalogInfo().setName(dataTypeChildVo.getName()).setPId(calculateCatalogId);
            busCatalogInfoService.addCatalog(calculateCatalogChild);
            stDataType.setCalculateCatalogId(calculateCatalogChild.getId());
        }
        Long productCatalogId = byIdGetParent.getProductCatalogId();
        if(productCatalogId!=null){
            BusCatalogInfo busCatalogInfo = busCatalogInfoService.getById(productCatalogId);
            if(busCatalogInfo==null){
                throw new GlobalException(byIdGetParent.getName()+"的产品目录不存在");
            }
            BusCatalogInfo productCatalogChild=new BusCatalogInfo().setName(dataTypeChildVo.getName()).setPId(productCatalogId);
            busCatalogInfoService.addCatalog(productCatalogChild);
            stDataType.setProductCatalogId(productCatalogChild.getId());
        }
        stDataType.setLevel(byIdGetParent.getLevel()+1);
        return save(stDataType);
    }

    @Override
    public boolean addParent(AddDataTypeParentVo aDTP) {
        StDataType dataType = new StDataType().setName(aDTP.getName()).setLevel(1).setPid(0L);
        BusCatalogInfo productCatalogInfo = new BusCatalogInfo().setName(aDTP.getName());
        busCatalogInfoService.addCatalog(productCatalogInfo);
        dataType.setProductCatalogId(productCatalogInfo.getId());
        if(aDTP.isCalculate()){
            BusCatalogInfo calculateCatalogChild = new BusCatalogInfo().setName(aDTP.getName());
            busCatalogInfoService.addCatalog(calculateCatalogChild);
            dataType.setProductCatalogId(calculateCatalogChild.getId());
        }
        return save(dataType);
    }


    @Override
    public boolean updateNameAndCatalogName(DataTypeUpdateNameVo dataTypeUpdateNameVo) {
        StDataType dataType = getById(dataTypeUpdateNameVo.getId());
        if(dataType==null){
            throw new GlobalException("数据不存在");
        }
        dataType.setName(dataTypeUpdateNameVo.getName());
        Long calculateCatalogId = dataType.getCalculateCatalogId();
        if(calculateCatalogId!=null){
            BusCatalogInfo busCatalogInfo = busCatalogInfoService.getById(calculateCatalogId);
            if(busCatalogInfo==null){
                throw new GlobalException(dataType.getName()+"的计算目录不存在");
            }
            busCatalogInfoService.updateCatalog(busCatalogInfo.setName(dataTypeUpdateNameVo.getName()));
        }
        Long productCatalogId = dataType.getProductCatalogId();
        if(productCatalogId!=null){
            BusCatalogInfo busCatalogInfo = busCatalogInfoService.getById(productCatalogId);
            if(busCatalogInfo==null){
                throw new GlobalException(dataType.getName()+"的产品目录不存在");
            }
            busCatalogInfoService.updateCatalog(busCatalogInfo.setName(dataTypeUpdateNameVo.getName()));
        }
        return updateById(dataType);
    }

    @Override
    public StDataType getResourceCatalogId(String firstName, String secondName) {
        StDataType firstDataType = getOne(new LambdaQueryWrapper<StDataType>().eq(StDataType::getName, firstName));
        if(firstDataType==null){
            throw new GlobalException("没有找到第一个名称的类型");
        }
        List<StDataType> list = list(new LambdaQueryWrapper<StDataType>().eq(StDataType::getPid, firstDataType.getId()));
        for (StDataType stDataType : list) {
            if (stDataType.getName().equals(secondName)) {
                return stDataType;
            }
        }
        throw new GlobalException("没有找到对应的数据");
    }

    @Override
    public boolean deleteDataTypeAndCatalog(Long id) {
        return rvById(id);
    }

    @Override
    public List<StDataType> getDataTypeByproductCatalogIdOrCalculateCatalogId(Long id) {
        return
            this.lambdaQuery()
            .eq(StDataType::getProductCatalogId,id)
            .or()
            .eq(StDataType::getCalculateCatalogId,id)
            .list();

    }

    @Override
    public StDataType getParent(Long parentId) {
        return this.lambdaQuery()
            .eq(StDataType::getId,parentId)
            .one();
    }

    @Override
    public SatelliteDto getSatelliteDto(Long catalogId) {
        List<StDataType> list = getDataTypeByproductCatalogIdOrCalculateCatalogId(catalogId);
        SatelliteDto satelliteDto = null; // 外部声明，避免作用域问题
        if (!list.isEmpty()) {
            StDataType dataType = list.get(0);
            StDataType parentType = getById(dataType.getPid());
            // 赋值（注意：parentType 可能为 null，建议加非空判断，避免 NPE）
            if (Objects.nonNull(parentType)) {
                satelliteDto = new SatelliteDto()
                    .setSatelliteName(parentType.getName())
                    .setSatelliteLevel(dataType.getName());
            }
        }
        return satelliteDto; // 无数据时返回 null
    }

    @Override
    public List<NewMenuVo> TreeByString(NewMenuQueryVo queryVo) {
        Integer type = queryVo.getType();
        if (type!=1&type!=2){
            throw new GlobalException("参数错误,不支持该类型");
        }
        List<StDataType> dataTypes = this.lambdaQuery().eq(StDataType::getName, queryVo.getName()).list();
        List<NewMenuVo> newMenuVos = new ArrayList<>();
        for (StDataType dataType : dataTypes) {
            StDataType parent = getParent(dataType.getPid());
            if(type==1&&parent.getProductCatalogId()!=null){
                newMenuVos.add(new NewMenuVo().setId(dataType.getProductCatalogId()).setName(parent.getName()));
            }else if(type==2&&parent.getCalculateCatalogId()!=null){
                newMenuVos.add(new NewMenuVo().setId(dataType.getCalculateCatalogId()).setName(parent.getName()));
            }
        }
        return newMenuVos;
    }

    @Override
    public boolean rvById(Long id) {
        StDataType stDataType= getById(id);
        List<StDataType> list = lambdaQuery().eq(StDataType::getPid, id).list();
        if (!list.isEmpty()) {
            for (StDataType dataType : list) {
               rvById(dataType);
            }
        }
        return rvById(stDataType);
    }

    public boolean rvById(StDataType dataType){
        if(dataType.getProductCatalogId()!=null){
            busCatalogInfoService.removeById(dataType.getProductCatalogId());
        }
        if(dataType.getProductCatalogId()!=null){
            busCatalogInfoService.removeById(dataType.getCalculateCatalogId());
        }
        return removeById(dataType.getId());
    }

}
