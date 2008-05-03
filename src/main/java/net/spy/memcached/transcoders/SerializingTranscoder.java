// Copyright (c) 2006  Dustin Sallings <dustin@spy.net>

package net.spy.memcached.transcoders;

import java.util.Date;

import net.spy.memcached.CachedData;

/**
 * Transcoder that serializes and compresses objects.
 */
public class SerializingTranscoder extends BaseSerializingTranscoder
	implements Transcoder<Object> {

	// General flags
	static final int SERIALIZED=1;
	static final int COMPRESSED=2;

	// Special flags for specially handled types.
	private static final int SPECIAL_MASK=0xff00;
	static final int SPECIAL_BOOLEAN=(1<<8);
	static final int SPECIAL_INT=(2<<8);
	static final int SPECIAL_LONG=(3<<8);
	static final int SPECIAL_DATE=(4<<8);
	static final int SPECIAL_BYTE=(5<<8);
	static final int SPECIAL_FLOAT=(6<<8);
	static final int SPECIAL_DOUBLE=(7<<8);
	static final int SPECIAL_BYTEARRAY=(8<<8);

	/* (non-Javadoc)
	 * @see net.spy.memcached.Transcoder#decode(net.spy.memcached.CachedData)
	 */
	public Object decode(CachedData d) {
		byte[] data=d.getData();
		Object rv=null;
		if((d.getFlags() & COMPRESSED) != 0) {
			data=decompress(d.getData());
		}
		int flags=d.getFlags() & SPECIAL_MASK;
		if((d.getFlags() & SERIALIZED) != 0 && data != null) {
			rv=deserialize(data);
		} else if(flags != 0 && data != null) {
			switch(flags) {
				case SPECIAL_BOOLEAN:
					rv=Boolean.valueOf(TranscoderUtils.decodeBoolean(data));
					break;
				case SPECIAL_INT:
					rv=new Integer(TranscoderUtils.decodeInt(data));
					break;
				case SPECIAL_LONG:
					rv=new Long(TranscoderUtils.decodeLong(data));
					break;
				case SPECIAL_DATE:
					rv=new Date(TranscoderUtils.decodeLong(data));
					break;
				case SPECIAL_BYTE:
					rv=new Byte(TranscoderUtils.decodeByte(data));
					break;
				case SPECIAL_FLOAT:
					rv=new Float(Float.intBitsToFloat(
							TranscoderUtils.decodeInt(data)));
					break;
				case SPECIAL_DOUBLE:
					rv=new Double(Double.longBitsToDouble(
							TranscoderUtils.decodeLong(data)));
					break;
				case SPECIAL_BYTEARRAY:
					rv=data;
					break;
				default:
					getLogger().warn("Undecodeable with flags %x", flags);
			}
		} else {
			rv=decodeString(data);
		}
		return rv;
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.Transcoder#encode(java.lang.Object)
	 */
	public CachedData encode(Object o) {
		byte[] b=null;
		int flags=0;
		if(o instanceof String) {
			b=encodeString((String)o);
		} else if(o instanceof Long) {
			b=TranscoderUtils.encodeLong((Long)o);
			flags |= SPECIAL_LONG;
		} else if(o instanceof Integer) {
			b=TranscoderUtils.encodeInt((Integer)o);
			flags |= SPECIAL_INT;
		} else if(o instanceof Boolean) {
			b=TranscoderUtils.encodeBoolean((Boolean)o);
			flags |= SPECIAL_BOOLEAN;
		} else if(o instanceof Date) {
			b=TranscoderUtils.encodeLong(((Date)o).getTime());
			flags |= SPECIAL_DATE;
		} else if(o instanceof Byte) {
			b=TranscoderUtils.encodeByte((Byte)o);
			flags |= SPECIAL_BYTE;
		} else if(o instanceof Float) {
			b=TranscoderUtils.encodeInt(Float.floatToRawIntBits((Float)o));
			flags |= SPECIAL_FLOAT;
		} else if(o instanceof Double) {
			b=TranscoderUtils.encodeLong(Double.doubleToRawLongBits((Double)o));
			flags |= SPECIAL_DOUBLE;
		} else if(o instanceof byte[]) {
			b=(byte[])o;
			flags |= SPECIAL_BYTEARRAY;
		} else {
			b=serialize(o);
			flags |= SERIALIZED;
		}
		assert b != null;
		if(b.length > compressionThreshold) {
			byte[] compressed=compress(b);
			if(compressed.length < b.length) {
				getLogger().info("Compressed %s from %d to %d",
					o.getClass().getName(), b.length, compressed.length);
				b=compressed;
				flags |= COMPRESSED;
			} else {
				getLogger().info(
					"Compression increased the size of %s from %d to %d",
					o.getClass().getName(), b.length, compressed.length);
			}
		}
		return new CachedData(flags, b);
	}

}
