package net.mossol.bot.model;

import lombok.Value;

@Value
public class SimpleText {
    private String message;
    private TextType type;
    private String response;
}
