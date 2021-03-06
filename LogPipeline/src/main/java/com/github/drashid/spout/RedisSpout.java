package com.github.drashid.spout;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Fields;
import backtype.storm.utils.Utils;
import com.github.drashid.redis.RedisModule;
import com.google.inject.Inject;

public class RedisSpout extends AbstractInjectedSpout {

  private static final long                 serialVersionUID = -1601631143587291210L;
  private LinkedBlockingQueue<RedisMessage> messageQueue;
  private SpoutOutputCollector              collector;  
  private ExecutorService                   service;
  
  @Inject
  private JedisPool                         pool;
  private PubSub                            pubsub;
  
  protected void _open(@SuppressWarnings("rawtypes") Map conf, TopologyContext context, SpoutOutputCollector collector) {
    this.collector = collector;
    messageQueue = new LinkedBlockingQueue<RedisMessage>();
    service = Executors.newSingleThreadExecutor();
    
    service.execute(new Runnable(){
      public void run() {
        Jedis jedis = pool.getResource();
        try {
          pubsub = new PubSub(messageQueue);
          jedis.psubscribe(pubsub, RedisModule.LOG_CHANNEL_ROOT + "*");
        }
        finally {
          pool.returnResource(jedis);
        }
      }
    });
  }

  @Override
  public void close() {
    pubsub.unsubscribe();
    service.shutdownNow();
  }
  
  public void nextTuple() {
    RedisMessage next = messageQueue.poll();
    if(next == null){
      Utils.sleep(100);
    }else{
      collector.emit(Utils.tuple(next.getChannel(), next.getMessage()));
    }
  }

  public void declareOutputFields(OutputFieldsDeclarer declarer) {
    declarer.declare(new Fields("source", "message"));
  }

  private static class RedisMessage {
    private final String message;
    private final String channel;
    
    public RedisMessage(String channel, String message) {
      this.message = message;
      this.channel = channel;
    }
    
    public String getMessage() {
      return message;
    }
    
    public String getChannel() {
      return channel;
    }
  }
  
  private static class PubSub extends JedisPubSub {
    
    private LinkedBlockingQueue<RedisMessage> queue;

    public PubSub(LinkedBlockingQueue<RedisMessage> queue) {
      this.queue = queue;
    }
    
    @Override
    public void onPMessage(String pattern, String channel, String message) {
      queue.offer(new RedisMessage(channel, message));
    }
    
    @Override
    public void onMessage(String channel, String message) {
      queue.offer(new RedisMessage(channel, message));
    }
    
    @Override
    public void onUnsubscribe(String arg0, int arg1) { }
    
    @Override
    public void onSubscribe(String arg0, int arg1) { }
    
    @Override
    public void onPUnsubscribe(String arg0, int arg1) { }
    
    @Override
    public void onPSubscribe(String arg0, int arg1) { }
    
  };
  
}
