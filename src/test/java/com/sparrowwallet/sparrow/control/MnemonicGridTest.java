package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Bip39MnemonicCode;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MnemonicGridTest {
    private static final int GRID_COLUMN_COUNT = 16;

    private List<String> generateMnemonic(int entropyLength) {
        SecureRandom secureRandom;
        try {
            secureRandom = SecureRandom.getInstanceStrong();
        } catch(NoSuchAlgorithmException e) {
            secureRandom = new SecureRandom();
        }

        return new DeterministicSeed(secureRandom, entropyLength, "").getMnemonicCode();
    }

    /**
     * Convert the shuffled word list into a grid of 4 letter abbreviations, as MnemonicGridDialog.toGrid does
     */
    private String[][] toGrid(List<String> words) {
        String[][] grid = new String[words.size()/GRID_COLUMN_COUNT][GRID_COLUMN_COUNT];
        int row = 0;
        int col = 0;
        for(String word : words) {
            String abbr = word.length() < 4 ? word : word.substring(0, 4);
            grid[row][col] = abbr;
            col++;
            if(col >= GRID_COLUMN_COUNT) {
                col = 0;
                row++;
            }
        }

        return grid;
    }

    /**
     * Resolve 4 letter abbreviations back to full words, as MnemonicGridDialog.getSelectedWords does
     */
    private List<String> fromGrid(String[][] grid, List<int[]> cells) {
        List<String> words = new ArrayList<>();
        for(int[] cell : cells) {
            String abbreviation = grid[cell[0]][cell[1]];
            for(String word : Bip39MnemonicCode.INSTANCE.getWordList()) {
                if((abbreviation.length() == 3 && word.equals(abbreviation)) || (abbreviation.length() >= 4 && word.startsWith(abbreviation))) {
                    words.add(word);
                    break;
                }
            }
        }

        Assertions.assertEquals(cells.size(), words.size());
        return words;
    }

    @Test
    public void testShuffleIsDeterministic() {
        List<String> gridSeed = generateMnemonic(128);
        Assertions.assertEquals(MnemonicGridDialog.shuffle(gridSeed), MnemonicGridDialog.shuffle(gridSeed));

        List<String> otherGridSeed = generateMnemonic(128);
        Assertions.assertNotEquals(MnemonicGridDialog.shuffle(gridSeed), MnemonicGridDialog.shuffle(otherGridSeed));
    }

    @Test
    public void testShuffleContainsAllWordsOnce() {
        List<String> gridSeed = generateMnemonic(128);
        List<String> shuffled = MnemonicGridDialog.shuffle(gridSeed);
        Assertions.assertEquals(2048, shuffled.size());
        Assertions.assertEquals(2048, new HashSet<>(shuffled).size());
        Assertions.assertEquals(new HashSet<>(Bip39MnemonicCode.INSTANCE.getWordList()), new HashSet<>(shuffled));
    }

    @Test
    public void testExistingSeedMapsOntoGridAndRecovers() throws MnemonicException {
        for(int wordCount : new int[] {12, 24}) {
            List<String> existingWords = generateMnemonic(wordCount == 12 ? 128 : 256);
            List<String> wordsToMap = existingWords.subList(0, existingWords.size() - 1);
            Assertions.assertEquals(wordsToMap.size(), new HashSet<>(wordsToMap).size());

            //A new grid seed is generated, and the grid built from it
            List<String> gridSeed = generateMnemonic(128);
            List<String> shuffledWordList = MnemonicGridDialog.shuffle(gridSeed);
            String[][] wordGrid = toGrid(shuffledWordList);

            //Locate the cells of the existing seed words, in order
            List<int[]> mappedCells = new ArrayList<>();
            for(String word : wordsToMap) {
                int index = shuffledWordList.indexOf(word);
                Assertions.assertTrue(index >= 0);
                mappedCells.add(new int[] {index / GRID_COLUMN_COUNT, index % GRID_COLUMN_COUNT});
            }

            //Simulate regenerating the grid from the recorded grid seed and reselecting the pattern
            String[][] regeneratedGrid = toGrid(MnemonicGridDialog.shuffle(gridSeed));
            List<String> recoveredWords = fromGrid(regeneratedGrid, mappedCells);

            //The selected words reproduce the existing seed words in order
            Assertions.assertEquals(wordsToMap, recoveredWords);

            //The final (checksum) word is recalculated, and matches the original
            Assertions.assertTrue(Bip39MnemonicCode.INSTANCE.getPossibleLastWords(recoveredWords).contains(existingWords.get(existingWords.size() - 1)));
        }
    }
}