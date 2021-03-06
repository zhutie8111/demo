package cn.com.tcsl.ws;

import cn.com.tcsl.ws.exception.WebSocketClientException;
import cn.com.tcsl.ws.message.DefaultReceiveMessage;
import cn.com.tcsl.ws.message.ReceiveMessage;
import cn.com.tcsl.ws.status.Heartbeat;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.URI;
import java.util.Map;

/**
 * Created by Tony on 2018/11/3.
 */
public class WebsocketPushClient {

    private String url;

    private int httpMaxContentLength = 8192;

    private WebsocketConfig websocketConfig;

    private ReceiveMessage receiveMessage;

    private Heartbeat heartbeat;

    private Channel channel;

    private EventLoopGroup groupCopy;


    //static final String URL = System.getProperty("url", "ws://192.168.9.215:9001/websocket?shopId=325");

    public WebsocketPushClient(WebsocketConfig config){

        this.websocketConfig = config;

        this.receiveMessage = new ReceiveMessage(){};
    }

    public WebsocketPushClient(WebsocketConfig config, ReceiveMessage callbackReceiver){

        this.websocketConfig = config;

        this.receiveMessage = callbackReceiver;
    }

    protected void connect() throws Exception{
        {
            getUrl();
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null? "ws" : uri.getScheme();
            final String host = uri.getHost() == null? "127.0.0.1" : uri.getHost();
            final int port;
            //如果没有设置端口号
            if (uri.getPort() == -1) {
                if ("ws".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("wss".equalsIgnoreCase(scheme)) {
                    port = 443;
                } else {
                    port = -1;
                }
            } else {
                port = uri.getPort();
            }

            if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
                System.err.println("Only WS(S) is supported.");
                throw new RuntimeException("Scheme was invalid, Only WS(S) is supported.");
            }

            final boolean ssl = "wss".equalsIgnoreCase(scheme);
            final SslContext sslCtx;
            if (ssl) {
                 sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                //sslCtx = SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE); //Only for 4.0.* version
            } else {
                sslCtx = null;
            }


            if (receiveMessage != null && websocketConfig.getUseDefaultMessageReceiver()){
                receiveMessage = new DefaultReceiveMessage();
            }

            EventLoopGroup group = new NioEventLoopGroup();
            try {
                // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
                // If you change it to V00, ping is not supported and remember to change
                // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
                final WebsocketPushClientHandler handler =
                        new WebsocketPushClientHandler(
                                WebSocketClientHandshakerFactory.newHandshaker(
                                        uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()), receiveMessage);

                handler.setWebsocketConfig(websocketConfig);

                handler.setHeartbeat(heartbeat);

                Bootstrap bootstrap = new Bootstrap();

                groupCopy = group;

                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                //wss 连接
                                if (sslCtx != null) {
                                    pipeline.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                                }
                                pipeline.addLast(
                                        new HttpClientCodec(),
                                        new HttpObjectAggregator(httpMaxContentLength),
                                        WebSocketClientCompressionHandler.INSTANCE,
                                        //new LoggingHandler(LogLevel.INFO), // only for debug
                                        new IdleStateHandler(websocketConfig.getReaderIdleTimeSeconds(),
                                                websocketConfig.getWriterIdleTimeSeconds(),
                                                websocketConfig.getAllIdleTimeSeconds()),
                                        handler);
                            }
                        }).option(ChannelOption.SO_KEEPALIVE, true)

                ;

                //连接服务器
                ChannelFuture channelFuture = bootstrap.connect(uri.getHost(), port).sync();
                channel = channelFuture.channel();
                handler.handshakeFuture().sync();

                //channel.closeFuture().sync();
            }catch (Exception e){
                groupCopy.shutdownGracefully();
                throw new WebSocketClientException("fail to establish a connection", e);
            }
            finally {
               // group.shutdownGracefully();

            }
        }

    }

    /**
     * According to config to create url
     */
    private void getUrl(){

        url = websocketConfig.getScheme() + "://" + websocketConfig.getHost()+":" + websocketConfig.getPort() + "/"
                + websocketConfig.getPath();

        if (websocketConfig.getSuffixParams() != null && !websocketConfig.getSuffixParams().isEmpty()){
            Map<String, Object> params = websocketConfig.getSuffixParams();
            StringBuilder paramsBuilder = new StringBuilder();
            for (String key : params.keySet()){
                paramsBuilder.append(key);
                paramsBuilder.append("=");
                paramsBuilder.append(params.get(key));
                paramsBuilder.append("&");
            }
            if (paramsBuilder.length() > 0){
                paramsBuilder.deleteCharAt(paramsBuilder.length() - 1);
            }
            url = url + "?" + paramsBuilder.toString();
        }

    }

    public void setWebsocketConfig(WebsocketConfig websocketConfig) {
        this.websocketConfig = websocketConfig;
    }

    public void setReceiveMessage(ReceiveMessage receiveMessage) {
        this.receiveMessage = receiveMessage;
    }

    public WebsocketConfig getWebsocketConfig() {
        return websocketConfig;
    }

    public ReceiveMessage getReceiveMessage() {
        return receiveMessage;
    }

    public Channel getChannel() {
        return channel;
    }

    public Heartbeat getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Heartbeat heartbeat) {
        this.heartbeat = heartbeat;
    }

    public EventLoopGroup getGroupCopy(){
        return groupCopy;
    }
}
