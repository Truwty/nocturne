package dev.truwty.nocturne.lyrics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;

final class ProviderBackoff {
    private static final ConcurrentHashMap<String,Instant> UNTIL=new ConcurrentHashMap<>();
    private ProviderBackoff() {}
    static void check(URI uri) throws IOException {
        Instant until=UNTIL.get(uri.getHost());
        if(until!=null && until.isAfter(Instant.now()))throw new IOException("Lyric provider cooling down; retry after "+until);
    }
    static void response(URI uri,int status,HttpHeaders headers) throws IOException {
        if(status!=429 && status!=503)return;
        Instant now=Instant.now(), until=retryAt(headers.firstValue("Retry-After").orElse("60"),now);
        UNTIL.merge(uri.getHost(),until,(a,b)->a.isAfter(b)?a:b);
        throw new IOException("Lyric provider HTTP "+status+"; retry after "+until);
    }
    static Instant retryAt(String value,Instant now) {
        try {return now.plusSeconds(Math.max(1,Long.parseLong(value.trim())));}
        catch(NumberFormatException | java.time.DateTimeException | ArithmeticException ex) {
            try {Instant date=ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();return date.isAfter(now)?date:now.plusSeconds(1);}
            catch(DateTimeParseException invalid) {return now.plusSeconds(60);}
        }
    }
}
