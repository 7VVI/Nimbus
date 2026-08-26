package com.nimbus.json.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置
 * <ul>
 *     <li>Long/BigInteger/BigDecimal 序列化为字符串, 避免前端精度丢失</li>
 *     <li>java.time 统一 yyyy-MM-dd HH:mm:ss 格式</li>
 *     <li>反序列化忽略未知字段</li>
 * </ul>
 * 通过 nimbus.json.enabled=false 可整体关闭
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nimbus.json", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JsonAutoConfiguration {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer nimbusJacksonCustomizer() {
        return builder -> builder
            .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .serializerByType(Long.class, ToStringSerializer.instance)
            .serializerByType(Long.TYPE, ToStringSerializer.instance)
            .serializerByType(BigInteger.class, ToStringSerializer.instance)
            .serializerByType(BigDecimal.class, ToStringSerializer.instance)
            .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME))
            .serializerByType(LocalDate.class, new LocalDateSerializer(DATE))
            .serializerByType(LocalTime.class, new LocalTimeSerializer(TIME))
            .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME))
            .deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE))
            .deserializerByType(LocalTime.class, new LocalTimeDeserializer(TIME));
    }
}