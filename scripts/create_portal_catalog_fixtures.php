<?php
declare(strict_types=1);
// Run only against the CI database. Generate fixtures with the exact published PHP29 serializer.
require dirname(__DIR__).'/portal-fixture/app/applications.php';
$migration=require ROOT.'/database/migrations/0.4.29.php';$migration(db());
function fixture_entity(int $tid,string $app,string $kind,string $name,?int $parent=null,?int $device=null):int{
 execsql('INSERT INTO application_entities(tenant_id,app,kind,name,parent_id,device_id,config_json,enabled) VALUES(?,?,?,?,?,?,?,1)',[$tid,$app,$kind,$name,$parent,$device,'{}']);return (int)db()->lastInsertId();
}
$room=fixture_entity(1,'turnos','room','Sala central');fixture_entity(1,'turnos','screen','TV sala',$room,1);fixture_entity(1,'turnos','service','Atención',$room);
$private=fixture_entity(1,'turnos','room','Sala privada');fixture_entity(1,'turnos','service','Servicio privado',$private);
$venue=fixture_entity(2,'sports','venue','Club central');$court=fixture_entity(2,'sports','court','Pista central',$venue);fixture_entity(2,'sports','screen','TV pista',$court,2);fixture_entity(2,'sports','team','Equipo central',$venue);
$other=fixture_entity(2,'sports','venue','Club privado');fixture_entity(2,'sports','team','Equipo privado',$other);
$out=dirname(__DIR__).'/app/src/androidTest/assets';if(!is_dir($out))mkdir($out,0700,true);
foreach([['turnos',1,1],['sports',2,2]] as [$app,$tid,$did]){
 execsql('INSERT INTO application_configs(tenant_id,app,revision,settings_json,updated_at) VALUES(?,?,9,?,UTC_TIMESTAMP())',[$tid,$app,json_encode(['title'=>'Empresa CI '.$tid])]);
 $json=json_encode(ds_app_snapshot($tid,$app,$did),JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);
 if(str_contains($json,'privad'))throw new RuntimeException('Private scope leaked');
 file_put_contents($out.'/portal-catalog-'.$app.'.json',$json);
}
echo "PHP29_CATALOG_FIXTURES_PASS: scoped real PHP JSON, ready for Android validation\n";
