package com.ghzdude.randomizer.api;

import net.minecraft.resources.Identifier;

import java.util.Map;

public interface RandomizerContext {

    Map<Identifier, Identifier> randomizer$getMap();
}
