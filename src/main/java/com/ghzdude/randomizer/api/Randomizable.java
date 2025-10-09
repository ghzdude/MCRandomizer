package com.ghzdude.randomizer.api;

import com.google.gson.JsonElement;
import net.minecraft.resources.RegistryOps;

public interface Randomizable {

    void randomizer$randomize(RegistryOps<JsonElement> ops);
}
