package com.htht.business.domain.dto;

import com.htht.business.domain.StInstruments;
import com.htht.business.domain.StInstrumentsInfo;
import com.htht.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode()
@ToString()
public class StInstrumentsAndInfoDTO {
    private Long id;

    @NotBlank(message="仪器编号不能为空")
    private String instrumentCode;

    @NotBlank(message="仪器名称不能为空")
    private String instrumentName;

    private Long imageId;

    private String manufacturer;

    private String distributor;

    private String parameters;

    private LocalDate commissionDate;

    private String useVoyage;

    private String manager;

    private BigDecimal longitude;

    private BigDecimal latitude;

    public StInstrumentsAndInfoDTO(){}

    public StInstrumentsAndInfoDTO(StInstruments stInstruments, StInstrumentsInfo stInstrumentsInfo){
        this.id = stInstruments.getId();
        this.instrumentCode = stInstruments.getInstrumentCode();
        this.instrumentName = stInstrumentsInfo.getInstrumentName();
        this.imageId = stInstrumentsInfo.getImageId();
        this.manufacturer = stInstrumentsInfo.getManufacturer();
        this.distributor = stInstrumentsInfo.getDistributor();
        this.parameters = stInstrumentsInfo.getParameters();
        this.commissionDate = stInstruments.getCommissionDate();
        this.useVoyage = stInstruments.getUseVoyage();
        this.manager = stInstruments.getManager();
        this.longitude = stInstruments.getLongitude();
        this.latitude = stInstruments.getLatitude();
    }

    public StInstruments toStInstruments(){
        return new StInstruments()
            .setId(this.id)
            .setInstrumentCode(this.instrumentCode)
            .setCommissionDate(this.commissionDate)
            .setUseVoyage(this.useVoyage)
            .setManager(this.manager);
    }

    public StInstrumentsInfo toStInstrumentsInfo(){
        return new StInstrumentsInfo()
            .setInstrumentName(this.instrumentName)
            .setImageId(this.imageId)
            .setManufacturer(this.manufacturer)
            .setDistributor(this.distributor)
            .setParameters(this.parameters);
    }
}
