package pl.srm.registrationapi.registration.util;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class PeselHelperTest {
    private final PeselHelper helper = new PeselHelper();
    private String pesel(String date) {
        String prefix = date + "1234";
        int[] weights = {1,3,7,9,1,3,7,9,1,3};
        int sum = 0;
        for (int i=0; i<10; i++) sum += (prefix.charAt(i)-'0') * weights[i];
        return prefix + ((10-sum%10)%10);
    }
    @Test void validatesChecksumAndCalendarDate() {
        assertTrue(helper.isValid(pesel("002229"))); // 2000 leap day
        assertFalse(helper.isValid(pesel("012229")));
        assertFalse(helper.isValid(pesel("002230")));
        assertFalse(helper.isValid(null));
        assertFalse(helper.isValid("abcdefghijk"));
        var valid = pesel("900101");
        assertFalse(helper.isValid(valid.substring(0,10) + ((valid.charAt(10)-'0'+1)%10)));
    }
    @Test void decodesAllCenturies() {
        var reference = LocalDate.of(2300,1,1);
        for (int[] value : new int[][]{{81,1800},{1,1900},{21,2000},{41,2100},{61,2200}}) {
            String code = pesel(String.format("00%02d01", value[0]));
            assertTrue(helper.isValid(code));
            assertEquals(2300-value[1],helper.calculateAge(code,reference));
        }
    }
    @Test void adulthoodChangesOnBirthday() {
        var code=pesel("082610"); // 10 June 2008
        assertTrue(helper.isMinor(code,LocalDate.of(2026,6,9)));
        assertFalse(helper.isMinor(code,LocalDate.of(2026,6,10)));
        assertEquals(18,helper.calculateAge(code,LocalDate.of(2026,8,1)));
    }
}
