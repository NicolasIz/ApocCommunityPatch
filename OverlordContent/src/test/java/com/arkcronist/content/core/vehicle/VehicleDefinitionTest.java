package com.arkcronist.content.core.vehicle;

import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.LoadReport;
import com.arkcronist.content.core.vehicle.VehicleDefinition.Offset;
import com.arkcronist.content.core.vehicle.VehicleDefinition.Part;
import com.arkcronist.content.core.vehicle.VehiclePhysics.State;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 4.0: vehicles as content files describe them, and how their displays are posed. */
class VehicleDefinitionTest {

    @TempDir
    Path contents;

    @Test
    void aVehicleReadsEveryPropertyAndDefaultsTheRest() throws IOException {
        write("demo/vehicles.yml", """
                items:
                  cart:
                    material: PAPER
                    vehicle:
                      max-speed: 10
                      reverse-speed: 2.5
                      acceleration: 5
                      braking: 20
                      coasting: 2
                      turn-rate: 120
                      step-height: 1.0
                      water-speed: 0.5
                      size: [1.4, 1.1, 2.4]
                      seats:
                        - [0.3, 0.7, 0.2]
                        - [-0.3, 0.7, -0.6]
                      parts:
                        - { item: demo:cart, offset: [0, 0.55, 0] }
                        - { item: demo:wheel, offset: [0.75, 0.3, 0.8], wheel: true, radius: 0.32, scale: 1.2 }
                  plain:
                    material: PAPER
                    vehicle: {}
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(List.of(), report.problems());
        VehicleDefinition cart = item(report, "demo:cart").vehicle();
        assertNotNull(cart);
        assertEquals(10, cart.maxSpeed());
        assertEquals(2.5, cart.reverseSpeed());
        assertEquals(5, cart.acceleration());
        assertEquals(20, cart.braking());
        assertEquals(2, cart.coasting());
        assertEquals(120, cart.turnRate());
        assertEquals(1.0, cart.stepHeight());
        assertEquals(0.5, cart.waterSpeed());
        assertEquals(1.4, cart.width(), 1e-6);
        assertEquals(1.1, cart.height(), 1e-6);
        assertEquals(2.4, cart.length(), 1e-6);
        assertEquals(List.of(new Offset(0.3, 0.7, 0.2), new Offset(-0.3, 0.7, -0.6)), cart.seats());
        assertEquals(new Part("demo:wheel", new Offset(0.75, 0.3, 0.8), 1.2, true, 0.32), cart.parts().get(1));

        VehicleDefinition plain = item(report, "demo:plain").vehicle();
        assertNotNull(plain);
        assertEquals(8, plain.maxSpeed());
        assertEquals(List.of(new Offset(0, 0.6, 0)), plain.seats());
        assertEquals(List.of(new Part(null, new Offset(0, 0.5, 0), 1, false, 0.3)), plain.parts(),
                "no parts: its own model, its middle half its height up");
    }

    @Test
    void outOfRangeValuesAreReportedAndKeptSensible() throws IOException {
        write("demo/vehicles.yml", """
                items:
                  wild:
                    material: PAPER
                    vehicle:
                      max-speed: 400
                      seats: [[0, 0.6]]
                      parts:
                        - { item: "Not An Id", offset: [0, 0, 0] }
                """);
        LoadReport report = new ContentLoader().load(contents);
        VehicleDefinition wild = item(report, "demo:wild").vehicle();
        assertEquals(8, wild.maxSpeed(), "out of range: the default");
        assertEquals(1, wild.seats().size(), "the broken seat is dropped; a vehicle always has a driver's seat");
        assertNull(wild.parts().getFirst().item(), "the broken part is dropped; its own model is drawn");
        List<String> problems = report.problems();
        assertTrue(problems.stream().anyMatch(p -> p.contains("max-speed")), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(p -> p.contains("seat 1")), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(p -> p.contains("part 1")), String.join("\n", problems));
    }

    @Test
    void aPartIsPlacedFromTheSeatItRidesAndTurnsWithTheVehicle() {
        Part body = new Part(null, new Offset(0, 0.5, 0), 1, false, 0.3);
        Offset driver = new Offset(0.4, 0.7, 0);
        VehiclePose.Transform facingSouth = VehiclePose.of(State.parked(0, 64, 0, 0), body, driver);
        // The driver sits 0.4 to the right - west, facing south - so the body's middle is 0.4 east of them.
        assertTrue(facingSouth.translation().equals(new Vector3f(0.4f, -0.2f, 0), 1e-6f), facingSouth.translation().toString());
        Vector3f front = drawn(facingSouth, new Vector3f(0, 0, 1));
        assertTrue(front.equals(new Vector3f(0, 0, 1), 1e-6f));

        VehiclePose.Transform facingWest = VehiclePose.of(State.parked(0, 64, 0, 90), body, driver);
        assertTrue(facingWest.translation().equals(new Vector3f(0, -0.2f, 0.4f), 1e-6f), facingWest.translation().toString());
        Vector3f turned = drawn(facingWest, new Vector3f(0, 0, 1));
        assertTrue(turned.equals(new Vector3f(-1, 0, 0), 1e-6f), "a model's south faces west: " + turned);
    }

    @Test
    void aWheelRollsForwardAsTheVehicleGoesForward() {
        Part wheel = new Part("demo:wheel", new Offset(0.7, 0.3, 0.8), 1, true, 0.25);
        State rolled = new State(0, 64, 0, 0, 4, 0, true, Math.PI * 0.25 / 2);   // a quarter turn
        VehiclePose.Transform pose = VehiclePose.of(rolled, wheel, new Offset(0, 0.6, 0));
        Vector3f top = drawn(pose, new Vector3f(0, 1, 0));
        assertTrue(top.equals(new Vector3f(0, 0, 1), 1e-5f), "its top has turned to the front: " + top);
    }

    /** Where a model's {@code v} ends up as the client draws it: its own half turn, then the rotation. */
    private static Vector3f drawn(VehiclePose.Transform pose, Vector3f v) {
        return pose.rotation().transform(VehiclePose.CLIENT_TURN.transform(v, new Vector3f()));
    }

    private static ItemDefinition item(LoadReport report, String id) {
        return report.items().stream().filter(item -> item.fullId().equals(id)).findFirst().orElseThrow();
    }

    private void write(String relative, String text) throws IOException {
        Path file = contents.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
