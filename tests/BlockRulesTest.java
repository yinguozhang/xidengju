package cn.xidengju.app;
public class BlockRulesTest {
    static void expect(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        expect(!BlockRules.validWindow(1410,1410));
        expect(BlockRules.validWindow(1410,420));
        expect(!BlockRules.validWindow(1440,420));
        expect(!BlockRules.inWindow(1409,1410,420));
        expect(BlockRules.inWindow(1410,1410,420));
        expect(BlockRules.inWindow(0,1410,420));
        expect(BlockRules.inWindow(419,1410,420));
        expect(!BlockRules.inWindow(420,1410,420));
        expect(!BlockRules.inWindow(600,600,600));
        expect(BlockRules.inWindow(600,500,700));
        expect(!BlockRules.shouldBlock(true,true,true,100,0,200,600,1410,420));
        expect(!BlockRules.shouldBlock(true,true,false,100,101,200,600,1410,420));
        expect(BlockRules.shouldBlock(true,true,false,100,100,200,600,1410,420));
        expect(!BlockRules.shouldBlock(true,true,false,200,0,200,600,1410,420));
        expect(!BlockRules.shouldBlock(false,true,false,100,0,200,600,1410,420));
        expect(!BlockRules.shouldBlock(true,false,false,100,0,200,600,1410,420));
        System.out.println("PASS: 16 timing, safety, unlock and test-expiry checks");
    }
}
