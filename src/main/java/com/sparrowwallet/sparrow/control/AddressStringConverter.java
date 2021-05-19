package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import javafx.util.StringConverter;

public class AddressStringConverter extends StringConverter<Address> {
    @Override
    public Address fromString(String value) {
        // If the specified value is null or zero-length, return null
        if(value == null) {
            return null;
        }

        value = value.trim();

        if (value.length() < 1) {
            return null;
        }

        try {
            return Address.fromString(value);
        } catch(InvalidAddressException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String toString(Address value) {
        // If the specified value is null, return a zero-length String
        if(value == null) {
            return "";
        }

        return value.toString();
    }
}
