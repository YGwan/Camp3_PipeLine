package com;

import com.Cpu.*;
import com.CpuOutput.*;
import com.Latch.EXE_MEM;
import com.Latch.ID_EXE;
import com.Latch.IF_ID;
import com.Latch.MEM_WB;
import com.Memory.Global;

import java.io.IOException;

public class Main extends Global {

    public static void main(String[] args) throws IOException {
        Logger.LOGGING_SIGNAL = true;
        Logger.LOGGING_COUNTER_SIGNAL = true;

//        test("source/simple.bin", 0);
        test("source/simple2.bin", 100);
//        test("source/simple3.bin", 5050);
//        test("source/simple4.bin", 55);
//        test("source/gcd.bin", 1);
//        test("source/fib.bin", 55);
//        test("source/input4.bin", 85);
    }

    private static void test(String path, int expect) throws IOException {
        int result = process(path);
        if(result != expect) {
            System.out.println("failed -> Path : " + path + " expected : " + expect + " real : " + result);
        }
        System.out.println("succeed -> Path : " + path + " expected : " + expect + " real : " + result);
    }


    private static int process(String path) throws IOException {
        initializedRegister();
        Global.init();

        Logger.println("\n--------------pipeline Start--------------");
        Logger.println("--------------" + path + " --------------\n");

        //Fetch 선언 및 Instruction Fetch
        MemoryFetch memoryFetch = new MemoryFetch(path);
        //memoryFetch.printBitInstruction();
        //memoryFetch.printHexInstruction();

        //객체 생성 - 하드웨어 컴포넌트 생성
        Decode decode = new Decode();
        Register register = new Register();
        PC pcUpdate = new PC();
        ALU alu = new ALU();
        Memory memory = new Memory();
        ForwardingUnit forwardingUnit = new ForwardingUnit();

        //Latch 선언
        IF_ID if_id = new IF_ID();
        ID_EXE id_exe = new ID_EXE();
        EXE_MEM exe_mem = new EXE_MEM();
        MEM_WB mem_wb = new MEM_WB();

        int cycleCount = 1;

        boolean inputInstEndPoint = false;
        boolean instEndPoint = false;

        while (!mem_wb.instEndPoint) {

            //total cycle 수 측정
            if (cycleCount % 1000000 == 0) {
                Logger.countPrintln("Cycle Count : %d\n", cycleCount++);
            }

            //--------------------------------------Start WriteBack Stage------------------------------------

            //MemToReg 값 구분 MUX
            int memToRegValue = register.memToRegSet(mem_wb.controlSignal, mem_wb.memoryCalcResult, mem_wb.finalAluResult);
            //writeBack
            register.registerWrite(mem_wb.controlSignal, memToRegValue, mem_wb.regDst);

            System.out.println("memTORegValue : "+ memToRegValue);

            //--------------------------------------Finish WriteBack Stage------------------------------------

            //-----------------------------------Start MemoryAccess Stage------------------------------------

            //loadUpper값 구분 Mux(LUI)
            int finalAluResult = memory.setAddress(exe_mem.controlSignal, id_exe.loadUpper, exe_mem.aluResult);

            //Memory Access
            MemoryOutput memoryOutput = memory.read(exe_mem.aluResult, exe_mem.controlSignal, exe_mem.instEndPoint);

            memory.write(exe_mem.aluResult, exe_mem.rtValue, exe_mem.controlSignal, exe_mem.instEndPoint);




            //-----------------------------------Finish MemoryAccess Stage------------------------------------


            //-----------------------------------------Start Fetch Stage--------------------------------------

            // instruction fetch
            MemoryFetchOutput memoryFetchOutput = memoryFetch.fetch(pc);
            String pcHex = Integer.toHexString(pc * 4);
            pc = nextPC;

            //------------------------------------Finish Fetch Stage------------------------------------

            //Latch
            if_id.input(memoryFetchOutput.nextPC, memoryFetchOutput.instruction, memoryFetchOutput.hexInstruction);


            //------------------------------------Start Decode Stage------------------------------------

            // instruction decode
            DecodeOutput decodeOutput = decode.decodeInstruction(if_id.instruction);

            RegisterOutput registerOutput = register.registerCalc(decodeOutput.rs,
                    decodeOutput.rt, decodeOutput.controlSignal);


            int readData1;
            int readData2;

//            //lw일때의 decode dataforwarding
//            if (Logger.onDataForwarding) {
//                boolean signalA = forwardingUnit.forwardDecodeInputValue(EXE_MEMValid, exe_mem.controlSignal,
//                        exe_mem.regDstValue, decodeOutput.rs);
//                boolean signalB = forwardingUnit.forwardDecodeInputValue(EXE_MEMValid, exe_mem.controlSignal,
//                        exe_mem.regDstValue, decodeOutput.rt);
//                readData1 = mux(signalA, memoryOutput.memoryCalcResult, registerOutput.firstRegisterOutput);
//                readData2 = mux(signalB, memoryOutput.memoryCalcResult, registerOutput.secondRegisterOutput);
//            } else {
//                readData1 = registerOutput.firstRegisterOutput;
//                readData2 = registerOutput.secondRegisterOutput;
//            }

            readData1 = registerOutput.firstRegisterOutput;
            readData2 = registerOutput.secondRegisterOutput;

            //------------------------------------Finish Decode Stage--------------------------------------

            //Latch
            id_exe.input(decodeOutput.controlSignal, if_id.nextPC, readData1, readData2,
                    decodeOutput.signExt, decodeOutput.zeroExt, decodeOutput.shamt, decodeOutput.jumpAddr, decodeOutput.branchAddr,
                    decodeOutput.loadUpperImm, decodeOutput.rs, decodeOutput.rt, decodeOutput.rd);

            //RegDst 값 구하기
            int regDstResult = decodeOutput.regDstSet(id_exe.controlSignal, id_exe.rt, id_exe.rd);

            //------------------------------------Data forwarding 처리--------------------------------------

            AluOutput aluOutput;

            if (Logger.onDataForwarding) {
                System.out.println("has hazard!!!!" );
                System.out.println();

                int signalA = forwardingUnit.forwardA(EXE_MEMValid, MEM_WBValid, exe_mem.controlSignal, mem_wb.controlSignal,
                        exe_mem.regDstValue, id_exe.rs, mem_wb.regDst);

                int signalB = forwardingUnit.forwardB(EXE_MEMValid, MEM_WBValid, exe_mem.controlSignal, mem_wb.controlSignal,
                        exe_mem.regDstValue, id_exe.rt, mem_wb.regDst, id_exe.rs);

                System.out.println(signalA);
                System.out.println(signalB);



                // forwardA･B MUX
                int aluInputData1 = forwardMux(signalA, id_exe.readData1, exe_mem.aluResult, memToRegValue);
                int aluInputData2 = forwardMux(signalB, id_exe.readData2, exe_mem.aluResult, memToRegValue);

                int aluInput1 = alu.setAluInput1(id_exe.controlSignal, id_exe.shamt, aluInputData1);
                int aluInput2 = alu.setAluInput2(id_exe.controlSignal, id_exe.signExt, id_exe.zeroExt, aluInputData2);

                System.out.println("///////r2");
                System.out.println(" signalA: " + signalA);
                System.out.println(" signalB: " + signalB);
                System.out.println(" aluInputData1: " + aluInputData1);
                System.out.println(" aluInputData2: " + aluInputData2);
                if(id_exe.controlSignal != null) {
                    System.out.println(" id_exe.controlSignal: " + id_exe.controlSignal.inst);
                }
                System.out.println(" aluInput1: " + aluInput1);
                System.out.println(" aluInput2: " + aluInput2);
                System.out.println(" exe_mem.aluResult : " + exe_mem.aluResult);
                System.out.println(" memToRegValue : " + memToRegValue);
                System.out.println(id_exe.rs + " " + aluInput1);
                System.out.println(id_exe.rt + " " + aluInput2);
                System.out.println("//////////");

                System.out.println(aluInput1 + " " + aluInput2);

                aluOutput = alu.process(id_exe.controlSignal, aluInput1, aluInput2);
            }

            //------------------------------------Start Execution Stage------------------------------------

            //Execution
            else {
                int aluInput1 = alu.setAluInput1(id_exe.controlSignal, id_exe.shamt, id_exe.readData1);
                int aluInput2 = alu.setAluInput2(id_exe.controlSignal, id_exe.signExt, id_exe.zeroExt, id_exe.readData2);
                aluOutput = alu.process(id_exe.controlSignal, aluInput1, aluInput2);
            }

            //pc Update - 문제가 발생할 수도 있다.
            pcUpdate.pcUpdate(id_exe.controlSignal, id_exe.readData1, aluOutput.aluResult, id_exe.jumpAddr, id_exe.branchAddr);


            //-----------------------------------pc == -1 일때 처리 -----------------------------------------

            if (pc == -1) {
                Global.FetchValid = false;
                Global.InputIF_IDValid = false;
                Global.InputID_EXEValid = false;
                inputInstEndPoint = true;
            }


            //------------------------------------Finish Execution Stage------------------------------------

            //Latch
            exe_mem.input(id_exe.controlSignal, id_exe.nextPc, id_exe.readData1,
                    id_exe.readData2, aluOutput.aluResult, regDstResult, instEndPoint);

            //Latch
            mem_wb.input(exe_mem.controlSignal, finalAluResult, memoryOutput.memoryCalcResult, exe_mem.regDstValue, exe_mem.instEndPoint);

            //출력 부분
            if (FetchValid) {
                Logger.println("cyl %d, IF Stage -> pc : 0x%s, instruction : 0x%s\n", cycleCount++, pcHex, if_id.inputHexInstruction);
            } else {
                Logger.println("cyl %d, IF Stage -> [NOP]\n", cycleCount++);
            }

            printLogo(register, pcUpdate, memory, exe_mem, mem_wb, decodeOutput, aluOutput);

            //latch Update
            if_id.output();
            id_exe.output();
            exe_mem.output();
            mem_wb.output();

            //Valid값 갱신
            Global.IF_IDValid = Global.InputIF_IDValid;
            Global.ID_EXEValid = Global.InputID_EXEValid;
            Global.EXE_MEMValid = Global.InputEXE_MEMValid;
            Global.MEM_WBValid = Global.InputMEM_WBValid;


            //instEndpoint 값 갱신

            instEndPoint = inputInstEndPoint;
        }

        System.out.printf("\nresult value R[2] : %d\n", Global.register[2]);
        return Global.register[2];
    }

    private static void printLogo(Register register, PC pcUpdate, Memory memory, EXE_MEM exe_mem, MEM_WB mem_wb, DecodeOutput decodeOutput, AluOutput aluOutput) {
        decodeOutput.printDecodeStage(decodeOutput.opcode, decodeOutput.rs, decodeOutput.rt);
        aluOutput.printExecutionOutput(aluOutput.aluResult);
        pcUpdate.pcUpdatePrint();
        memory.printExecutionMemoryAccess(exe_mem.controlSignal, exe_mem.aluResult, exe_mem.rtValue, exe_mem.instEndPoint);
        register.printExecutionWriteBack(mem_wb.controlSignal, mem_wb.regDst);
        Logger.println();
    }

    public static int mux(boolean signal, int trueVal, int falseVal) {
        if (signal) {
            return trueVal;
        }
        return falseVal;
    }

    public static int forwardMux(int signal, int basic, int exe_memReturnVale, int mem_wbReturnValue) {

        int returnValue;

        switch (signal) {
            case 0:
                returnValue = basic;
                break;
            case 1:
                returnValue = exe_memReturnVale;
                break;
            case 2:
                returnValue = mem_wbReturnValue;
                break;

            default:
                returnValue = 0;
        }

        return returnValue;
    }

    private static void initializedRegister() {
        pc = 0;
        register[29] = 0x1000000;
        register[31] = 0xFFFFFFFF;
    }

}
