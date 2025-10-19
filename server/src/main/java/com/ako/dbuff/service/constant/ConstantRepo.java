package com.ako.dbuff.service.constant;

import com.ako.dbuff.config.DotaApiConstant;
import com.ako.dbuff.service.constant.data.ConstantData;
import java.util.Map;

public interface ConstantRepo<T extends ConstantData> {

  DotaApiConstant getConstantId();

  Map<String, T> getConstantMap();
}
