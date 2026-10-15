#!/usr/bin/env python3
"""Create a small PNG icon without requiring Pillow or Android vector support (API 19)."""
import os, struct, zlib, math
W=192
def segment(x,y,ax,ay,bx,by):
    dx,dy=bx-ax,by-ay
    t=max(0,min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)))
    return math.hypot(x-(ax+t*dx),y-(ay+t*dy))
def inside_tri(x,y,a,b,c):
    def sign(p,q,r):return (p[0]-r[0])*(q[1]-r[1])-(q[0]-r[0])*(p[1]-r[1])
    p=(x,y);x1=sign(p,a,b)<0;x2=sign(p,b,c)<0;x3=sign(p,c,a)<0
    return x1==x2==x3
def pixel(ix,iy):
    x,y=ix*108/W,iy*108/W
    # White rounded-square logo with generous transparent icon padding.
    if not (9<=x<=99 and 9<=y<=99):return (0,0,0,0)
    if x<22 and y<22 and math.hypot(x-22,y-22)>13:return (0,0,0,0)
    if x>86 and y<22 and math.hypot(x-86,y-22)>13:return (0,0,0,0)
    if x<22 and y>86 and math.hypot(x-22,y-86)>13:return (0,0,0,0)
    if x>86 and y>86 and math.hypot(x-86,y-86)>13:return (0,0,0,0)
    c=(255,255,255,255)
    stroke=[(21,45),(25,34),(36,27),(50,27),(57,33),(56,42),(23,69),(58,69)]
    if min(segment(x,y,*a,*b) for a,b in zip(stroke,stroke[1:]))<5.3:c=(13,174,67,255)
    # Navy C encircling the green play triangle on the right
    angle=math.degrees(math.atan2(y-56,x-79))
    if 49<angle<311 and abs(math.hypot(x-79,y-56)-21)<5.4:c=(23,69,137,255)
    if inside_tri(x,y,(66,17),(93,29),(66,41)):c=(5,180,78,255)
    return c
raw=b''.join(b'\x00'+bytes(channel for x in range(W) for channel in pixel(x,y)) for y in range(W))
def chunk(tag,data):return struct.pack('!I',len(data))+tag+data+struct.pack('!I',zlib.crc32(tag+data)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',W,W,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
dst='app/src/main/res/drawable-nodpi/ic_2cam_signage.png'
os.makedirs(os.path.dirname(dst),exist_ok=True)
with open(dst,'wb') as f:f.write(png)
print('2Cam PNG launcher icon created:',dst,len(png),'bytes')
