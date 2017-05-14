package io.liang.jsidekiq.client.consumer.thread;

import io.liang.jsidekiq.client.ClientManager;
import io.liang.jsidekiq.client.exception.NoFoundClassOrMethodException;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.liang.jsidekiq.client.aop.JSidekiqRole;
import io.liang.jsidekiq.client.common.Cons;
import io.liang.jsidekiq.client.config.Config;
import io.liang.jsidekiq.client.pojo.Element;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;

/**
 * Created by zhiping on 17/4/16.
 */
public abstract class Template {
    protected Logger log = LoggerFactory.getLogger(this.getClass());

    protected ConsumerThreadPool threadPool;
    protected ClientManager clientManager;

    protected Element element;

    public abstract void excute()throws NoFoundClassOrMethodException,NoSuchMethodException,
    InvocationTargetException,IllegalAccessException, IllegalArgumentException;

    public void run(){
        JSidekiqRole.setRoleConsumer();

        String queueName = null;
        Exception exception = null;
        try {
            excute();
        }catch (NoFoundClassOrMethodException e) {  //dead
            queueName = Cons.SCHEDULE_DEAD;
            exception = e;

            log.error(e.getMessage(),e);
        } catch (NoSuchMethodException e) { //dead
            queueName = Cons.SCHEDULE_DEAD;
            exception = e;

            log.error(e.getMessage(),e);
        } catch (InvocationTargetException e) {  //retry
            queueName = Cons.SCHEDULE_RETRY;
            exception = e;

            log.error(e.getMessage(),e);
        } catch (IllegalAccessException e) {   //retry
            queueName = Cons.SCHEDULE_RETRY;
            exception = e;

            log.error(e.getMessage(),e);
        }catch (Exception e){   //retry
            queueName = Cons.SCHEDULE_RETRY;
            exception = e;

            log.error(e.getMessage(),e);
        }finally{
            clientManager.incrementProcessed(1L);

            attemptRetry(exception,queueName);

            close();
        }

    }

    /**
     * 暂时出错了。都放入重试队列
     * @param e
     * @param queueName  队列名称
     */
    public void attemptRetry(Exception e,String queueName){
        if(StringUtils.isNotBlank(queueName)) {
            String errorStack = io.liang.jsidekiq.client.common.utils.StringUtils.getExceptionStack(e);

            element.setErrorMessage(errorStack);

            Integer retry = element.getRetry();
            if(retry == null){
                retry = 0;
            }
            Integer maxRetry = element.getMaxRetry();
            if(maxRetry == null){
                maxRetry = Config.getInstance().getMaxRetry();
            }

            Long now = System.currentTimeMillis();
            retry++;
            if(maxRetry < retry){
                element.setFailedAt(new Date(now));
            }else{
                element.setRetry(retry);
                element.setRetriedAt(new Date(now + getRetryCount(retry)));
            }

            clientManager.push(element);

            clientManager.incrementFailed(1L);
        }
    }


    /**
     * 计算重试时间
     * @param count
     * @return
     */
    public long getRetryCount(Integer count){
        return (long)(Math.pow(count,4) + 15 + (RandomUtils.nextInt(0,30) * (count + 1))) * 1000L;
    }


    public abstract void close();

    public void setThreadPool(ConsumerThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public void setClientManager(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public void setElement(Element element) {
        this.element = element;
    }
}
