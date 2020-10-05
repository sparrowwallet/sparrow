package com.sparrowwallet.sparrow.ur;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

/**
 * Ported from https://github.com/BlockchainCommons/URKit
 */
public class Bytewords {
    public static final String BYTEWORDS = "ableacidalsoapexaquaarchatomauntawayaxisbackbaldbarnbeltbetabiasbluebodybragbrewbulbbuzzcalmcashcatschefcityclawcodecolacookcostcruxcurlcuspcyandarkdatadaysdelidicedietdoordowndrawdropdrumdulldutyeacheasyechoedgeepicevenexamexiteyesfactfairfernfigsfilmfishfizzflapflewfluxfoxyfreefrogfuelfundgalagamegeargemsgiftgirlglowgoodgraygrimgurugushgyrohalfhanghardhawkheathelphighhillholyhopehornhutsicedideaidleinchinkyintoirisironitemjadejazzjoinjoltjowljudojugsjumpjunkjurykeepkenokeptkeyskickkilnkingkitekiwiknoblamblavalazyleaflegsliarlimplionlistlogoloudloveluaulucklungmainmanymathmazememomenumeowmildmintmissmonknailnavyneednewsnextnoonnotenumbobeyoboeomitonyxopenovalowlspaidpartpeckplaypluspoempoolposepuffpumapurrquadquizraceramprealredorichroadrockroofrubyruinrunsrustsafesagascarsetssilkskewslotsoapsolosongstubsurfswantacotasktaxitenttiedtimetinytoiltombtoystriptunatwinuglyundouniturgeuservastveryvetovialvibeviewvisavoidvowswallwandwarmwaspwavewaxywebswhatwhenwhizwolfworkyankyawnyellyogayurtzapszerozestzinczonezoom";
    private static final List<String> bytewordsList;
    private static final List<String> minimalBytewordsList;

    static {
        bytewordsList = getBytewords();
        minimalBytewordsList = getMinimalBytewords();
    }

    public enum Style {
        STANDARD, URI, MINIMAL
    }

    public static int getEncodedLength(int length, Style style) {
        if(style == Style.STANDARD || style == Style.URI) {
            return length * 4 + (length - 1);
        }

        return length * 2;
    }

    public static String encode(byte[] data, Style style) {
        if(style == Style.STANDARD) {
            return encode(data, " ");
        }
        if(style == Style.URI) {
            return encode(data, "-");
        }

        return encodeMinimal(data);
    }

    public static byte[] decode(String encoded, Style style) {
        if(style == Style.STANDARD) {
            return decode(encoded, " ");
        }
        if(style == Style.URI) {
            return decode(encoded, "-");
        }

        return decodeMinimal(encoded);
    }

    private static String encode(byte[] data, String separator) {
        byte[] dataAndChecksum = appendChecksum(data);
        List<String> words = IntStream.range(0, dataAndChecksum.length).map(index -> dataAndChecksum[index] & 0xFF).mapToObj(Bytewords::getByteword).collect(Collectors.toList());
        StringJoiner joiner = new StringJoiner(separator);
        words.forEach(joiner::add);
        return joiner.toString();
    }

    private static String encodeMinimal(byte[] data) {
        byte[] dataAndChecksum = appendChecksum(data);
        List<String> words = IntStream.range(0, dataAndChecksum.length).map(index -> dataAndChecksum[index] & 0xFF).mapToObj(Bytewords::getMinimalByteword).collect(Collectors.toList());
        StringBuilder buffer = new StringBuilder();
        words.forEach(buffer::append);
        return buffer.toString();
    }

    private static byte[] decode(String encoded, String separator) {
        String[] words = encoded.split(separator);
        byte[] data = toByteArray(Arrays.stream(words).mapToInt(word -> getBytewords().indexOf(word)));
        return stripChecksum(data);
    }

    private static byte[] decodeMinimal(String encoded) {
        List<String> words = splitStringBySize(encoded, 2);
        byte[] data = toByteArray(words.stream().mapToInt(word -> getMinimalBytewords().indexOf(word)));
        return stripChecksum(data);
    }

    private static byte[] appendChecksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);

        ByteBuffer checksum = ByteBuffer.allocate(Long.BYTES);
        checksum.putLong(crc.getValue());

        byte[] result = new byte[data.length + 4];
        System.arraycopy(data, 0, result, 0, data.length);
        System.arraycopy(checksum.array(), 4, result, data.length, 4);

        return result;
    }

    private static byte[] stripChecksum(byte[] dataAndChecksum) {
        byte[] data = Arrays.copyOfRange(dataAndChecksum, 0, dataAndChecksum.length - 4);
        byte[] checksum = Arrays.copyOfRange(dataAndChecksum, dataAndChecksum.length - 4, dataAndChecksum.length);

        CRC32 crc = new CRC32();
        crc.update(data);

        ByteBuffer calculedChecksum = ByteBuffer.allocate(Long.BYTES);
        calculedChecksum.putLong(crc.getValue());
        if(!Arrays.equals(Arrays.copyOfRange(calculedChecksum.array(), 4, 8), checksum)) {
            throw new InvalidChecksumException("Invalid checksum");
        }

        return data;
    }

    private static String getByteword(int dataByte) {
        return bytewordsList.get(dataByte);
    }

    private static String getMinimalByteword(int dataByte) {
        return minimalBytewordsList.get(dataByte);
    }

    private static List<String> getBytewords() {
        return IntStream.range(0, 256).mapToObj(i -> BYTEWORDS.substring(i * 4, (i * 4) + 4)).collect(Collectors.toList());
    }

    private static List<String> getMinimalBytewords() {
        return IntStream.range(0, 256).mapToObj(i -> Character.toString(BYTEWORDS.charAt(i * 4)) + BYTEWORDS.charAt((i * 4) + 3)).collect(Collectors.toList());
    }

    public static byte[] toByteArray(IntStream stream) {
        return stream.collect(ByteArrayOutputStream::new, (baos, i) -> baos.write((byte) i),
                (baos1, baos2) -> baos1.write(baos2.toByteArray(), 0, baos2.size()))
                .toByteArray();
    }

    private static List<String> splitStringBySize(String str, int size) {
        List<String> split = new ArrayList<>();
        for(int i = 0; i < str.length() / size; i++) {
            split.add(str.substring(i * size, Math.min((i + 1) * size, str.length())));
        }
        return split;
    }

    public static class InvalidChecksumException extends RuntimeException {
        public InvalidChecksumException(String message) {
            super(message);
        }
    }
}
