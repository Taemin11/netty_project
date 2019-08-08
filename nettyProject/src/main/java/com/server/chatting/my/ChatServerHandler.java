//git test 0808
package com.server.chatting.my;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
 
public class ChatServerHandler extends ChannelInboundHandlerAdapter {
 
    private static final ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    static final AttributeKey<String> nickAttr = AttributeKey.newInstance("nickname");
    private static final NicknameProvider nicknameProvider = new NicknameProvider();
 
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    	Channel incoming = ctx.channel();
    	helo(incoming);
        System.out.println("handlerAdded of [SERVER] : " + nickname(incoming));        
        for (Channel channel : channelGroup) {
        	
            channel.write("[SERVER] - " + "[" + nickname(incoming) + "]" + " has joined!\n");
            
        }
        channelGroup.add(incoming);
    }
 
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    	Channel incoming = ctx.channel();
    	helo(incoming);
        System.out.println("User Access!");
        
    }   
    
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    	Channel incoming = ctx.channel();
        System.out.println("handlerRemoved of [SERVER] : " + nickname(incoming));        
        for (Channel channel : channelGroup) {
        	
            channel.write("[SERVER] - " + "[" + nickname(incoming) + "]" + " has left!\n");
        }
        channelGroup.remove(incoming);
        channelGroup.writeAndFlush(M("LEFT", nickname(ctx)));
        nicknameProvider.release(nickname(ctx));
    }
 
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
 
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    	Channel incoming = ctx.channel();
    	String message = null;
        message = (String)msg;
        System.out.println("[" + nickname(incoming) + "] : " + message);        
        for (Channel channel : channelGroup) {
            if (channel != incoming) {
            	
                channel.writeAndFlush("[" + nickname(incoming) + "] : " + message + "\n");
            }
        }
        if ("!bye".equals(message.toLowerCase())) {
            ctx.close();
        }
        else if ("!users".equals(message.toLowerCase())) {
        	int count = 0;
        	for (Channel channel : channelGroup) {
        		count++;
        		if (channel != incoming) {
        			incoming.writeAndFlush(nickname(channel) + "\n");
        		}
        		else {
        			incoming.writeAndFlush("me : " + nickname(channel) + "\n");
        		}
        	}
        	incoming.writeAndFlush("number of users : " + count);
        }
        else if ("!mynick".equals(message.toLowerCase())) {
        	for (Channel channel : channelGroup) {
                if (channel == incoming) {                    
                	incoming.writeAndFlush(nickname(channel) + "\n");
                }
            }
        }
        else if ("!command".equals(message.toLowerCase())){
        	for (Channel channel : channelGroup) {
                if (channel == incoming) {                    
                	incoming.writeAndFlush("command lsit : !bye !users !mynick !command");
                }
            }
        }
    	
    }
    
    private ChatMessage M(String... args) {
        switch (args.length) {
            case 1:
                return new ChatMessage(args[0]);
            case 2:
                return new ChatMessage(args[0], args[1]);
            case 3:
                ChatMessage m = new ChatMessage(args[0], args[1]);
                m.text = args[2];
                return m;
            default:
                throw new IllegalArgumentException();
        }
    }
    
    private void bindNickname(Channel c, String nickname) {
        c.attr(nickAttr).set(nickname);
    }
    
    private String nickname(Channel c) {
        return c.attr(nickAttr).get();
    }
    
    private String nickname(ChannelHandlerContext ctx) {
        return nickname(ctx.channel());
    }
    
    private void helo(Channel ch) {
        if (nickname(ch) != null) return; // already done;
        String nick = nicknameProvider.reserve();
        if (nick == null) {
            ch.writeAndFlush(M("ERR", "sorry, no more names for you"))
                    .addListener(ChannelFutureListener.CLOSE);
        } else {
            bindNickname(ch, nick);
            channelGroup.forEach(c -> ch.write(M("HAVE", nickname(c))));
            channelGroup.writeAndFlush(M("JOIN", nick));
            channelGroup.add(ch);
            ch.writeAndFlush(M("HELO", nick));
        }
    }
     
}
