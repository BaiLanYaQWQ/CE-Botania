package net.bailanya.cebotania.core;

import net.bailanya.cebotania.block.ManaBlockController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TickBusTest {
    @ParameterizedTest
    @ValueSource(ints = {511, 512, 513, 1024, 1025})
    void ticksEveryRegisteredControllerOncePerPump(int count) throws ReflectiveOperationException {
        TickBus bus = new TickBus(null);
        List<CountingController> controllers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CountingController controller = new CountingController();
            controllers.add(controller);
            bus.register(controller);
        }
        CountingController removed = controllers.removeLast();
        CountingController retained = controllers.getFirst();
        bus.register(retained);

        Method pump = TickBus.class.getDeclaredMethod("pump");
        pump.setAccessible(true);
        for (int tick = 1; tick <= 100; tick++) {
            pump.invoke(bus);
            assertEquals(tick, removed.ticks);
            for (CountingController controller : controllers) {
                assertEquals(tick, controller.ticks);
            }
        }

        bus.unregister(removed);
        pump.invoke(bus);
        assertEquals(100, removed.ticks);
        for (CountingController controller : controllers) {
            assertEquals(101, controller.ticks);
        }
    }

    private static final class CountingController extends ManaBlockController {
        private int ticks;

        private CountingController() {
            super(null, 0);
        }

        @Override
        protected String rootKey() {
            return "tick-bus-test";
        }

        @Override
        public void serverTick() {
            ticks++;
        }
    }
}
