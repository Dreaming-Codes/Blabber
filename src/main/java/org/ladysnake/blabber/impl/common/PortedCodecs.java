package org.ladysnake.blabber.impl.common;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.Codecs;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class PortedCodecs {
    public static Codec<GameProfile> GAME_PROFILE;

    static {
        Codec<Property> GAME_PROFILE_PROPERTY = RecordCodecBuilder.create((instance) -> instance.group(Codec.STRING.fieldOf("name").forGetter(Property::getName), Codec.STRING.fieldOf("value").forGetter(Property::getValue), Codec.STRING.optionalFieldOf("signature").forGetter((property) -> Optional.ofNullable(property.getSignature()))).apply(instance, (key, value, signature) -> new Property(key, value, signature.orElse(null))));
        Codec<PropertyMap> GAME_PROFILE_PROPERTY_MAP = Codec.either(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()), GAME_PROFILE_PROPERTY.listOf()).xmap((either) -> {
            PropertyMap propertyMap = new PropertyMap();
            either.ifLeft((map) -> map.forEach((key, values) -> {
                for (String string : values) {
                    propertyMap.put(key, new Property(key, string));
                }
            })).ifRight((properties) -> {
                for (Property property : properties) {
                    propertyMap.put(property.getName(), property);
                }
            });
            return propertyMap;
        }, (properties) -> com.mojang.datafixers.util.Either.right(properties.values().stream().toList()));
        GAME_PROFILE = RecordCodecBuilder.create((instance) -> instance.group(Codec.mapPair(Codecs.UUID.xmap(Optional::of, (optional) -> optional.orElse(null)).optionalFieldOf("id", Optional.empty()), Codec.STRING.xmap(Optional::of, (optional) -> optional.orElse(null)).optionalFieldOf("name", Optional.empty())).flatXmap(PortedCodecs::createGameProfileFromPair, PortedCodecs::createPairFromGameProfile).forGetter(Function.identity()), GAME_PROFILE_PROPERTY_MAP.optionalFieldOf("properties", new PropertyMap()).forGetter(GameProfile::getProperties)).apply(instance, (profile, properties) -> {
            properties.forEach((key, property) -> {
                profile.getProperties().put(key, property);
            });
            return profile;
        }));
    }

    private static DataResult<GameProfile> createGameProfileFromPair(Pair<Optional<UUID>, Optional<String>> pair) {
        try {
            return DataResult.success(new GameProfile((UUID) ((Optional) pair.getFirst()).orElse(null), (String) ((Optional) pair.getSecond()).orElse(null)));
        } catch (Throwable var2) {
            Throwable throwable = var2;
            Objects.requireNonNull(throwable);
            return DataResult.error(throwable.getMessage());
        }
    }

    private static DataResult<Pair<Optional<UUID>, Optional<String>>> createPairFromGameProfile(GameProfile profile) {
        return DataResult.success(Pair.of(Optional.ofNullable(profile.getId()), Optional.ofNullable(profile.getName())));
    }
}
