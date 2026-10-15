package com.meiyetec.service;

import com.meiyetec.entity.DcepMerchantQualification;

public interface DcepMerchantQualificationService
        extends com.baomidou.mybatisplus.extension.service.IService<DcepMerchantQualification> {

    /**
     * 拿当前（或唯一一条）资质记录，没有就 new 一个空对象返回（id=null）。
     */
    DcepMerchantQualification currentOrInit();
}