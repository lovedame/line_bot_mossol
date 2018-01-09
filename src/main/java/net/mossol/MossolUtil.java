package net.mossol;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.mossol.model.LineRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Amos.Doan.Mac on 2017. 12. 6..
 */
public final class MossolUtil {
    private static final Logger logger = LoggerFactory.getLogger(MossolUtil.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static LineRequest readJsonString(String jsonString) {
        try {
            LineRequest request;
            request = OBJECT_MAPPER.readValue(jsonString, LineRequest.class);
            return request;
        } catch (Exception e) {
            logger.debug("[ERROR] Converting to object failed. Received Json String <{}>", jsonString);
            logger.debug("[ERROR] Test <{}>", jsonString);
            return null;
        }
    }

    public static String writeJsonString(Object obj) {
        try {
            if (obj == null) {
                return null;
            }
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }


}
