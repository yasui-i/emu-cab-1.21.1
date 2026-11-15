"""Copy the supplied mono Vorbis recordings verbatim; validate Ogg page CRCs."""
import argparse, hashlib, json, math, re, shutil, struct
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('source',type=Path)
args=parser.parse_args()
entries=re.findall(r'^\s*([A-Z_]+)\("([^"]+)",(\d+),(\d+)\)',
    (ROOT/'src/main/java/com/yasui_i/emucab/control/AtpAnnouncement.java').read_text(encoding='utf-8'),re.M)
assert len(entries)==15
assets=ROOT/'src/main/resources/assets/emucab'
target=assets/'sounds/atp';target.mkdir(parents=True,exist_ok=True)
sounds=json.loads((assets/'sounds.json').read_text(encoding='utf-8')) if (assets/'sounds.json').exists() else {}
report=[]
for name,source_name,ticks,priority in entries:
    source=args.source/(source_name+'.ogg');data=source.read_bytes()
    at=0;granule=0;packet=bytearray();packets=[];serial=None;sequence=0
    while at<len(data):
        assert data[at:at+4]==b'OggS' and data[at+4]==0,source
        segments=data[at+26];lacing=data[at+27:at+27+segments]
        size=27+segments+sum(lacing);page=bytearray(data[at:at+size]);assert len(page)==size,source
        checksum=struct.unpack_from('<I',page,22)[0];page[22:26]=bytes(4);crc=0
        for byte in page:
            crc^=byte<<24
            for _ in range(8):crc=((crc<<1)^(0x04c11db7 if crc&0x80000000 else 0))&0xffffffff
        assert crc==checksum,(source,'Ogg CRC mismatch')
        current_serial=struct.unpack_from('<I',page,14)[0]
        if serial is None:serial=current_serial
        assert serial==current_serial and struct.unpack_from('<I',page,18)[0]==sequence,source
        sequence+=1;granule=struct.unpack_from('<Q',page,6)[0]
        start=27+segments
        for length in lacing:
            packet.extend(page[start:start+length]);start+=length
            if length<255:packets.append(bytes(packet));packet.clear()
        at+=size
    assert not packet and data[5]&2 and page[5]&4,source
    header=packets[0];assert header.startswith(b'\x01vorbis') and len(header)==30,source
    channels=header[11];rate=struct.unpack_from('<I',header,12)[0]
    assert channels==1 and rate>0,source
    assert packets[1].startswith(b'\x03vorbis') and packets[2].startswith(b'\x05vorbis'),source
    duration=granule/rate;assert int(ticks)>=math.ceil(duration*20)+8,source
    cue=name.lower();destination=target/(cue+'.ogg');shutil.copyfile(source,destination)
    sounds['atp.'+cue]={'subtitle':'subtitle.emucab.atp.'+cue,'sounds':[{'name':'emucab:atp/'+cue,'stream':False,'attenuation_distance':8}]}
    report.append(dict(id=cue,source=str(source),source_name=source_name,seconds=round(duration,4),channels=channels,
                       sample_rate=rate,playback_ticks=int(ticks),sha256=hashlib.sha256(data).hexdigest()))
(assets/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
out=ROOT/'model-source/atp-audio';out.mkdir(parents=True,exist_ok=True)
(out/'recordings.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Imported and validated',len(report),'mono Vorbis recordings; original audio bytes preserved.')
