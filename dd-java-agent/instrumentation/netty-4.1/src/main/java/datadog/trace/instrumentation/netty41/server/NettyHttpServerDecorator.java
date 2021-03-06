package datadog.trace.instrumentation.netty41.server;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;

import datadog.trace.agent.decorator.HttpServerDecorator;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyHttpServerDecorator
    extends HttpServerDecorator<HttpRequest, Channel, HttpResponse> {
  public static final NettyHttpServerDecorator DECORATE = new NettyHttpServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"netty", "netty-4.0"};
  }

  @Override
  protected String component() {
    return "netty";
  }

  @Override
  protected String method(final HttpRequest httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  protected URI url(final HttpRequest request) {
    // FIXME: This code is duplicated across netty integrations.
    try {
      URI uri = new URI(request.uri());
      if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
        uri = new URI("http://" + request.headers().get(HOST) + request.uri());
      }
      return new URI(
          uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null);
    } catch (final URISyntaxException e) {
      log.debug("Cannot parse netty uri: {}", request.uri());
      return null;
    }
  }

  @Override
  protected String peerHostname(final Channel channel) {
    final SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getHostName();
    }
    return null;
  }

  @Override
  protected String peerHostIP(final Channel channel) {
    final SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected Integer peerPort(final Channel channel) {
    final SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getPort();
    }
    return null;
  }

  @Override
  protected Integer status(final HttpResponse httpResponse) {
    return httpResponse.status().code();
  }
}
