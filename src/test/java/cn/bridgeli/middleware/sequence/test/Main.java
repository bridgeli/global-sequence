package cn.bridgeli.middleware.sequence.test;

import cn.bridgeli.middleware.sequence.DynamicSeqService;
import cn.bridgeli.middleware.sequence.FixedSeqService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:sequence-context.xml")
public class Main {
    @Test
    public void test() {
        while (true) {
            System.out.println("dynamic" + DynamicSeqService.nextSeq("bridgeli"));
            System.out.println("fixed" + FixedSeqService.nextSeq("bridgeli"));
        }
    }
}
