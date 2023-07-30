package net.goui.flogger.testing;

import com.google.common.flogger.FluentLogger;
import java.util.logging.Level;
import net.goui.flogger.testing.junit5.FloggerTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

public class FloggerTestExtensionTest {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @RegisterExtension
  static final FloggerTestExtension logged =
      FloggerTestExtension.forClass(FloggerTestExtensionTest.class, Level.INFO);

  @ExtendWith(FloggerTestExtension.class)
  @Test
  public void testFoo() {
    logger.atInfo().log("Hello World!");

    logged.assertLog(0).contains("Hello");
  }
}
