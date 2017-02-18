package com.acorcs.wni.service;

import com.acorcs.wni.entity.GeometryEntity;
import com.acorcs.wni.entity.Notice;
import com.acorcs.wni.entity.WniEntity;
import com.acorcs.wni.mybatis.mapper.*;
import com.acorcs.wni.utils.ClassUtil;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by dengc on 2017/1/2.
 */
@Component
public class AffectedService {
    @Autowired
    private NoticeMapper noticeMapper;
    @Autowired
    private CustomRestrictedAreaMapper customRestrictedAreaMapper;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    static final Logger logger = LoggerFactory.getLogger(AffectedService.class);

    public List<GeometryEntity> findAffectWni(Geometry geometry, Date queryTime){
        List<GeometryEntity> result = new ArrayList<>();
        List<Notice> notices = noticeMapper.getValidNotice(queryTime);
        List<Future<List<GeometryEntity>>> futures = new ArrayList<>();
        logger.debug("查询到notice共{}条",notices.size());
        for(Notice notice:notices){
            List<Class> childrenMapper = ClassUtil.getAllClassByInterface(WniEntityMapper.class);
            for(Class mapper:childrenMapper){
                WniEntityMapper wniEntityMapper = (WniEntityMapper)applicationContext.getBean(mapper);
                List<GeometryEntity> entities = wniEntityMapper.findByNoticeId(notice.getId());

                logger.debug("{}通过notice[{}]查询到{}条entity",mapper.getSimpleName(),notice.getId(),entities.size());

                Callable<List<GeometryEntity>> task = new Callable<List<GeometryEntity>>(){
                    @Override
                    public List<GeometryEntity> call() throws Exception {

                        List<GeometryEntity> taskResult = new ArrayList<GeometryEntity>();
                        for(GeometryEntity entity:entities){

                            if(entity.getGeographic()==null){
                                break;
                            }
                            if(geometry instanceof Point || geometry instanceof MultiPoint){
                                if(entity.getGeographic().contains(geometry)){
                                    logger.debug("确定一个匹配entity[{}]",entity.getId());
                                    taskResult.add(entity);
                                }
                            }else if(geometry instanceof LineString){
                                if(entity.getGeographic().intersects(geometry)){
                                    logger.debug("确定一个匹配entity[{}]",entity.getId());
                                    taskResult.add(entity);
                                }
                            }

                        }
                        return taskResult;
                    }
                };

                Future<List<GeometryEntity>> future = taskExecutor.submit(task);
                futures.add(future);
            }
        }
        //TODO:增加自定义区域查询
        try {
            for(Future<List<GeometryEntity>> future :futures){

                result.addAll(future.get());
            }
            logger.debug("共获取到{}条匹配entity",result.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return result;
    }

}
