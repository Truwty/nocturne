package dev.truwty.nocturne.playback;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Complete the HTTP response only after a bounded body, so request timeouts cover downloads. */
public final class BoundedBody {
    private BoundedBody() {}

    public static HttpResponse.BodyHandler<byte[]> handler(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Invalid response limit");
        return info ->
                new HttpResponse.BodySubscriber<byte[]>() {
                    private final HttpResponse.BodySubscriber<byte[]> delegate =
                            HttpResponse.BodySubscribers.ofByteArray();
                    private Flow.Subscription upstream;
                    private long received;
                    private boolean failed;

                    @Override
                    public CompletionStage<byte[]> getBody() {
                        return delegate.getBody();
                    }

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        upstream = subscription;
                        delegate.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(List<ByteBuffer> items) {
                        if (failed) return;
                        for (var item : items) received += item.remaining();
                        if (received > maximum) {
                            failed = true;
                            upstream.cancel();
                            delegate.onError(new IOException("Response exceeds download limit"));
                            return;
                        }
                        delegate.onNext(items);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!failed) {
                            failed = true;
                            delegate.onError(error);
                        }
                    }

                    @Override
                    public void onComplete() {
                        if (!failed) delegate.onComplete();
                    }
                };
    }
}
