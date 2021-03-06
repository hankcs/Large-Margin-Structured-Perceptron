/**
 * Ported by Eraldo R. Fernandes from the C version from:
 *  
 *   http://code.google.com/p/smhasher/wiki/MurmurHash3
 *   
 */

package br.pucrio.inf.learn.util;

/**
 * MurmurHash3: a non-cryptographic hash function.
 * 
 * This code was converted from the orginal C version from:
 * 
 * http://code.google.com/p/smhasher/wiki/MurmurHash3
 * 
 * @author eraldof
 * 
 */
public class Murmur3Hash {

	/**
	 * Generate a 32-bit hash value for the given block of data.
	 * 
	 * @param data
	 *            a block of data in bytes.
	 * @param seed
	 *            the seed.
	 * @return the murmur hash code for the given data chunk.
	 */
	public static int hash32(byte[] data, int seed) {
		int c1 = 0xcc9e2d51;
		int c2 = 0x1b873593;

		/*
		 * Process body: full 4-byte blocks.
		 */
		int i = 0;
		int h1 = seed;
		int len = data.length;
		while (i <= len - 4) {
			int k1 = data[i + 0] & 0xFF;
			k1 |= (data[i + 1] & 0xFF) << 8;
			k1 |= (data[i + 2] & 0xFF) << 16;
			k1 |= (data[i + 3] & 0xFF) << 24;

			k1 *= c1;
			k1 = (k1 << 15) | (k1 >>> (32 - 15)); // ROTL32(k1,15);
			k1 *= c2;

			h1 ^= k1;
			h1 = (h1 << 13) | (h1 >>> (32 - 13)); // ROTL32(h1,13);
			h1 = h1 * 5 + 0xe6546b64;

			i += 4;
		}

		/*
		 * Process tail: last block smaller than 4 bytes.
		 */
		int k1 = 0;
		switch (len - i) {
		case 3:
			k1 ^= data[i + 2] << 16;
		case 2:
			k1 ^= data[i + 1] << 8;
		case 1:
			k1 ^= data[i];
			k1 *= c1;
			k1 = (k1 << 16) | (k1 >>> (32 - 16)); // ROTL32(k1, 16);
			k1 *= c2;
			h1 ^= k1;
		}

		/*
		 * Finalization.
		 */
		h1 ^= len;
		// BEGIN: h1 = fmix(h1);
		h1 ^= h1 >>> 16;
		h1 *= 0x85ebca6b;
		h1 ^= h1 >>> 13;
		h1 *= 0xc2b2ae35;
		h1 ^= h1 >>> 16;
		// END: h1 = fmix(h1);

		return h1;
	}

	/**
	 * Test the output of the Murmur3 hash function.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		int seed = 11;
		String in = "eraldo luis rezende fernandes";

		int code = hash32(in.getBytes(), seed);
		System.out.println("Output for " + in + ": " + code);
		assert code == -66495689 : "Output code (" + code + ") should be "
				+ -66495689 + " instead.";

		in = in.substring(0, in.length() - 1);
		code = hash32(in.getBytes(), seed);
		System.out.println("Output for " + in + ": " + code);
		assert code == 1613448023 : "Output code (" + code + ") should be "
				+ 1613448023 + " instead.";

		in = in.substring(0, in.length() - 1);
		code = hash32(in.getBytes(), seed);
		System.out.println("Output for " + in + ": " + code);
		assert code == 1047051053 : "Output code (" + code + ") should be "
				+ 1047051053 + " instead.";

		in = in.substring(0, in.length() - 1);
		code = hash32(in.getBytes(), seed);
		System.out.println("Output for " + in + ": " + code);
		assert code == 1729083322 : "Output code (" + code + ") should be "
				+ 1729083322 + " instead.";

		in = in.substring(0, in.length() - 1);
		code = hash32(in.getBytes(), seed);
		System.out.println("Output for " + in + ": " + code);
		assert code == -228221106 : "Output code (" + code + ") should be "
				+ -228221106 + " instead.";
	}
}
