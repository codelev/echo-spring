package echo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.UUID;

@org.springframework.web.bind.annotation.RestController
public class RestController {
    private static final UUID ID = UUID.randomUUID();
    private static final int delay = Integer.parseInt(System.getenv().getOrDefault("DELAY", "0"));

    @RequestMapping("/rest/echo")
    public String echo(HttpServletRequest request) throws Exception {
        // Introduce the delay
        if (delay > 0) {
            Thread.sleep(1000 * delay);
        }

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String protocol = request.getProtocol();
        StringBuilder rawRequest = new StringBuilder();

        // protocol
        rawRequest.append(method).append(" ").append(uri).append(" ").append(protocol).append("\n");

        // headers
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            rawRequest.append(headerName).append(": ").append(headerValue).append("\n");
        }

        // instance id
        rawRequest.append("ID").append(": ").append(ID).append("\n");

        // body hash
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = request.getInputStream().read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        byte[] hashBytes = digest.digest();
        rawRequest.append(HexFormat.of().formatHex(hashBytes)).append("\n");

        return rawRequest.toString();
    }
}
