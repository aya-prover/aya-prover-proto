// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import org.aya.lsp.language.AyaLanguageClient;
import org.aya.lsp.server.AyaServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class Lsp extends LspArgs implements Callable<Integer> {
  @Override
  public Integer call() throws Exception {
    Log.i("Hello, this is Aya language server");
    var startup = switch (mode) {
      case server -> runServer();
      case client -> runClient();
      case debug -> runDebug();
    };

    var executor = Executors.newSingleThreadExecutor(f -> new Thread(f, "client"));
    var server = new AyaServer();
    var launcher = Launcher.createLauncher(
      server,
      AyaLanguageClient.class,
      startup.in,
      startup.out,
      executor,
      Function.identity()
    );
    server.connect(launcher.getRemoteProxy());
    launcher.startListening();
    return 0;
  }

  private static @NotNull Startup runDebug() {
    Log.i("Debug mode, using stdin and stdout");
    return new Startup(System.in, System.out);
  }

  private @NotNull Startup runClient() throws IOException {
    Log.i("Client mode, connecting to %s:%d", host, port);
    var socket = new Socket(host, port);
    return new Startup(new CloseAwareInputStream(socket.getInputStream()), socket.getOutputStream());
  }

  private @NotNull Startup runServer() throws IOException {
    Log.i("Server mode, listening on %s:%d", host, port);
    try (var server = new ServerSocket(port, 0, InetAddress.getByName(host))) {
      var client = server.accept();
      return new Startup(new CloseAwareInputStream(client.getInputStream()), client.getOutputStream());
    }
  }

  private static class CloseAwareInputStream extends InputStream {
    private final InputStream inputStream;

    private CloseAwareInputStream(InputStream inputStream) {
      this.inputStream = inputStream;
    }

    private int closeIfRemoteClosed(int len) throws IOException {
      if (len < 0) inputStream.close();
      return len;
    }

    @Override public int read() throws IOException {
      return closeIfRemoteClosed(inputStream.read());
    }

    @Override public int read(byte @NotNull [] b) throws IOException {
      return closeIfRemoteClosed(inputStream.read(b));
    }

    @Override public int read(byte @NotNull [] b, int off, int len) throws IOException {
      return closeIfRemoteClosed(inputStream.read(b, off, len));
    }
  }

  private static record Startup(
    @NotNull InputStream in,
    @NotNull OutputStream out
  ) {
  }
}
