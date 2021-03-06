package net.mossol.bot.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import net.mossol.bot.model.ReplyMessage;
import net.mossol.bot.model.TextType;
import net.mossol.bot.service.MessageHandler;
import net.mossol.bot.util.MessageBuildUtil;
import net.mossol.bot.util.MossolUtil;

import com.fasterxml.jackson.databind.JsonNode;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Path;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.RequestObject;

@Service
public class MossolMessageController {
    private static final Logger logger = LoggerFactory.getLogger(MossolMessageController.class);

    @Resource
    private MessageHandler messageHandler;

    @Post
    @Path("/getMessage")
    public HttpResponse getMessage(@RequestObject JsonNode request) {
        final String message = request.get("message").textValue();
        final Map<String, String> ret = new HashMap<>();
        HttpResponse httpResponse;

        logger.info("request {}", request);

        ReplyMessage replyMessage;
        try {
            replyMessage = messageHandler.replyMessage(message);
            if (replyMessage == null) {
                logger.debug("INFO: there is no matching reply message");
                throw new Exception();
            }
        } catch (Exception e) {
            httpResponse = HttpResponse.of(HttpStatus.NOT_FOUND, MediaType.JSON_UTF_8, MossolUtil
                    .writeJsonString(Collections.emptyMap()));
            logger.debug("httpResponse <{}>", httpResponse);
            return httpResponse;
        }

        TextType type = replyMessage.getType();

        String response;
        switch(type) {
            case SELECT_MENU_K:
            case SELECT_MENU_J:
            case SELECT_MENU_D:
                final String foodMessage = MessageBuildUtil.sendFoodMessage(replyMessage.getLocationInfo());
                ret.put("message", foodMessage);
                response = MossolUtil.writeJsonString(ret);
                httpResponse = HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, response);
                return httpResponse;
            case LEAVE_ROOM:
                break;
            default:
                ret.put("message", replyMessage.getText());
                response = MossolUtil.writeJsonString(ret);
                httpResponse = HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, response);
                return httpResponse;
        }

        httpResponse = HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR);
        return httpResponse;
    }
}
