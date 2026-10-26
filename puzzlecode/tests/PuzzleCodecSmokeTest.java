package ru.sector9.puzzlecode;

import java.util.Random;

/** Runs on plain Java 17 in CI, independent of Android SDK. */
public final class PuzzleCodecSmokeTest {
    public static void main(String[] args) {
        Random rng = new Random(91237L);
        for (int grid : new int[]{4, 100}) {
            int count=grid*grid;
            for (int attempt=0; attempt<40; attempt++) {
                int seed=rng.nextInt();
                if(seed==0) seed=1;
                int[] destinations=PuzzleCodec.destinationsForSeed(grid,seed);
                PuzzleCodec.validate(destinations);
                int[] current=new int[count];
                for(int i=0;i<count;i++) current[i]=i;
                for(int i=0;i<200;i++) {
                    int a=rng.nextInt(count),b=rng.nextInt(count);
                    int temp=current[a];current[a]=current[b];current[b]=temp;
                }
                int[][] swaps=PuzzleCodec.swapsToSolve(current,destinations);
                if(swaps.length>=count) throw new AssertionError("Too many swaps");
                for (int[] swap : swaps) {
                    int tmp=current[swap[0]];
                    current[swap[0]]=current[swap[1]];
                    current[swap[1]]=tmp;
                }
                for(int i=0;i<count;i++)
                    if(destinations[current[i]]!=i) throw new AssertionError("Mismatch "+grid+"/"+attempt+" at "+i);
            }
        }
        int[] p=PuzzleCodec.decode("PZ1.4.AwgPBQoCDgsJDA0BBAYHAA.9CE17885",4,"9CE17885");
        if(p.length!=16) throw new AssertionError("Legacy code");
        int[] q=PuzzleCodec.decode("PZ2.100.13579BDF.AABBCCDD",100,"AABBCCDD");
        if(q.length!=10000) throw new AssertionError("New code");
        System.out.println("PASS: 40 trials for both grids, + legacy and new codes");
    }
}
